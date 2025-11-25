#!/usr/bin/env python3
import csv
import json
import math
from pathlib import Path

import matplotlib.animation as animation
import matplotlib.pyplot as plt
import networkx as nx

ROOT = Path(".")
topo_path = ROOT / "exports" / "topology.json"
migrations_path = ROOT / "logs" / "migrations.json"
faults_path = ROOT / "logs" / "faults.json"
load_path = ROOT / "logs" / "load.csv"
resources_path = ROOT / "logs" / "resources.csv"
out_mp4 = ROOT / "results" / "plots" / "topology_migrations.mp4"
out_gif = ROOT / "results" / "plots" / "topology_migrations.gif"

# Tunables
WINDOW = 120.0     # seconds shown per frame
FPS = 15
DURATION = 5 * 60      # seconds of video
MAX_ARROWS = 50    # limit clutter


def load_topology():
    data = json.loads(topo_path.read_text())
    G = nx.Graph()
    for n in data["nodes"]:
        G.add_node(n["id"], type=n.get("type", "node"))
    for e in data["links"]:
        G.add_edge(
            e["source"],
            e["target"],
            bw=e.get("bandwidthMbps", 0),
            lat=e.get("latencyMs", 0),
        )
    return G


def load_json_lines(path: Path):
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def load_latest_loads(path: Path):
    """Return latest cpu/mem/bw load per fog node from load.csv."""
    if not path.exists():
        return {}, {}
    latest = {}
    history = {}
    with path.open() as f:
        reader = csv.DictReader(f)
        for row in reader:
            fog = row.get("fog")
            try:
                t = float(row.get("time", 0))
                cpu = float(row.get("cpuLoad", 0))
                mem = float(row.get("memLoad", 0))
                bw = float(row.get("bwLoad", 0))
            except Exception:
                continue
            history.setdefault(fog, []).append((t, cpu, mem, bw))
            if fog not in latest or t >= latest[fog][0]:
                latest[fog] = (t, cpu, mem, bw)
    return {k: (v[1], v[2], v[3]) for k, v in latest.items()}, history


def load_resource_history(path: Path):
    """Return per-server load history and server->fog mapping from resources.csv."""
    history = {}
    server_fog = {}
    if not path.exists():
        return history, server_fog
    with path.open() as f:
        reader = csv.DictReader(f)
        for row in reader:
            server = row.get("server")
            fog = row.get("fog")
            try:
                t = float(row.get("time", 0))
                cpu = float(row.get("cpuLoad", 0))
                mem = float(row.get("memLoad", 0))
                bw = float(row.get("bwLoad", 0))
            except Exception:
                continue
            server_fog[server] = fog
            history.setdefault(server, []).append((t, cpu, mem, bw, fog))
    return history, server_fog


def main():
    G = load_topology()
    migs = load_json_lines(migrations_path)
    faults = load_json_lines(faults_path)
    latest_loads, load_history = load_latest_loads(load_path)
    resource_history, server_fog = load_resource_history(resources_path)
    fault_windows = build_fault_windows(faults)
    migs = sorted(migs, key=lambda m: m.get("start", 0))
    if not migs:
        print("No migrations found.")
        return

    t_min = min(m.get("start", 0) for m in migs)
    t_max = max(m.get("finish", 0) for m in migs)
    total_time = t_max - t_min
    target_frames = FPS * DURATION
    step = max(total_time / max(target_frames, 1), 1e-6)

    pos = clustered_layout(G)
    type_color = {
        "cloud": "#1f77b4",
        "fog-node": "#2ca02c",
        "fog-server": "#9467bd",
        "edge-device": "#7f7f7f",
    }
    node_colors = [
        type_color.get(G.nodes[n].get("type"), "#17becf")
        for n in G.nodes
    ]

    fig, (ax_net, ax_bar) = plt.subplots(
        1, 2, figsize=(14, 8), gridspec_kw={"width_ratios": [2.2, 1]}
    )
    nx.draw_networkx_edges(G, pos, alpha=0.25, width=1.0, edge_color="#bbbbbb", ax=ax_net)
    nx.draw_networkx_nodes(
        G,
        pos,
        node_color=node_colors,
        node_size=200,
        linewidths=0.5,
        edgecolors="#222",
        ax=ax_net,
    )
    nx.draw_networkx_labels(G, pos, font_size=7, ax=ax_net)
    ax_net.set_title("collab-ft topology with migrations/faults")

    arrow_artists = []

    def update(frame_idx):
        nonlocal arrow_artists
        for art in arrow_artists:
            art.remove()
        arrow_artists = []

        now = t_min + frame_idx * step
        window_start = max(t_min, now - WINDOW)
        window_end = now

        recent = [m for m in migs if window_start <= m.get("start", 0) <= window_end]
        if len(recent) > MAX_ARROWS:
            recent = recent[-MAX_ARROWS:]

        for m in recent:
            src, dst = m.get("from"), m.get("to")
            if src in pos and dst in pos:
                xs, ys = pos[src]
                xd, yd = pos[dst]
                frac = (now - m.get("start", 0)) / max(
                    (m.get("finish", 0) - m.get("start", 0)), 1e-6
                )
                frac = min(max(frac, 0), 1)
                xm, ym = xs + (xd - xs) * frac, ys + (yd - ys) * frac
                arr = ax_net.arrow(
                    xs,
                    ys,
                    xm - xs,
                    ym - ys,
                    length_includes_head=True,
                    head_width=0.02,
                    head_length=0.03,
                    alpha=0.7,
                    color="#d62728",
                    linewidth=1.5,
                    zorder=3,
                )
                arrow_artists.append(arr)

        for f in faults:
            ft = f.get("failedAt", f.get("time", f.get("failureTime", 0)))
            if window_start <= ft <= window_end:
                node = f.get("server")
                if node in pos:
                    x, y = pos[node]
                    scat = ax_net.scatter(
                        [x],
                        [y],
                        marker="X",
                        color="#ff7f0e",
                        s=150,
                        zorder=6,
                        alpha=0.9,
                        linewidths=1.5,
                        edgecolors="#000",
                    )
                    arrow_artists.append(scat)

        # Bar chart for this timestep (per server, grouped by fog)
        ax_bar.clear()
        rows = resources_at_time(resource_history, server_fog, now)
        y_pos = list(range(len(rows)))
        bar_height = 0.2
        ax_bar.barh([y + bar_height * 0 for y in y_pos], [r["cpu"] for r in rows], height=bar_height, color="#1f77b4", label="CPU")
        ax_bar.barh([y + bar_height * 1 for y in y_pos], [r["mem"] for r in rows], height=bar_height, color="#2ca02c", label="Memory")
        ax_bar.barh([y + bar_height * 2 for y in y_pos], [r["bw"] for r in rows], height=bar_height, color="#d62728", label="Bandwidth")
        ax_bar.set_yticks([y + bar_height for y in y_pos])
        ax_bar.set_yticklabels([f'{r["fog"]}/{r["server"]}' for r in rows], fontsize=6)
        ax_bar.set_xlim(0, 1)
        ax_bar.set_xlabel("Normalized load")
        ax_bar.set_title(f"Server load snapshot t={now:.1f}s")
        ax_bar.legend(fontsize=6)
        ax_bar.grid(axis="x", alpha=0.2, linewidth=0.5)
        # Fault indicator on bars
        failed_now = failed_servers(fault_windows, now)
        for idx, r in enumerate(rows):
            if r["server"] in failed_now:
                ax_bar.scatter(1.02, idx + bar_height, marker="X", color="#ff7f0e", s=40, zorder=5, clip_on=False)

        ax_net.set_title(f"collab-ft migrations/faults (t={now:.1f}s)")

    frames = math.ceil((total_time / step))
    ani = animation.FuncAnimation(fig, update, frames=frames, interval=1000 / FPS, blit=False)
    out_mp4.parent.mkdir(parents=True, exist_ok=True)
    try:
        ani.save(out_mp4, writer="ffmpeg", fps=FPS, dpi=150)
        print(f"Saved {out_mp4}")
    except Exception as e:
        fallback = out_gif
        ani.save(fallback, writer="pillow", fps=FPS, dpi=150)
        print(f"ffmpeg unavailable ({e}); saved GIF to {fallback}")

def clustered_layout(G: nx.Graph()):
    """Place fog nodes on a ring, with their servers/edges clustered nearby and cloud at center."""
    pos = {}
    fog_nodes = [n for n, d in G.nodes(data=True) if d.get("type") == "fog-node"]
    servers = [n for n, d in G.nodes(data=True) if d.get("type") == "fog-server"]
    edges = [n for n, d in G.nodes(data=True) if d.get("type") == "edge-device"]
    cloud_nodes = [n for n, d in G.nodes(data=True) if d.get("type") == "cloud"]

    # Cloud at origin
    for c in cloud_nodes:
        pos[c] = (0.0, 0.0)

    # Fog nodes on circle
    r = 2.0
    for idx, fog in enumerate(sorted(fog_nodes)):
        angle = 2 * math.pi * idx / max(len(fog_nodes), 1)
        pos[fog] = (r * math.cos(angle), r * math.sin(angle))

    def parent_name(name: str, marker: str):
        if marker in name:
            return name.split(marker)[0]
        return ""

    # Cluster servers
    for fog in fog_nodes:
        fog_pos = pos.get(fog, (0, 0))
        children = [s for s in servers if parent_name(s, "-server") == fog]
        for i, srv in enumerate(children):
            ang = 2 * math.pi * i / max(len(children), 1)
            rad = 0.35
            pos[srv] = (
                fog_pos[0] + rad * math.cos(ang),
                fog_pos[1] + rad * math.sin(ang),
            )

    # Cluster edges
    for fog in fog_nodes:
        fog_pos = pos.get(fog, (0, 0))
        children = [e for e in edges if parent_name(e, "-edge") == fog]
        for i, ed in enumerate(children):
            ang = 2 * math.pi * i / max(len(children), 1)
            rad = 0.55
            pos[ed] = (
                fog_pos[0] + rad * math.cos(ang),
                fog_pos[1] + rad * math.sin(ang),
            )

    # Fallback for any remaining nodes
    for n in G.nodes:
        if n not in pos:
            pos[n] = (0.0, 0.0)
    return pos


def save_load_bars(loads, type_color):
    """Save grouped progress bars for CPU/MEM/BW per fog/cloud."""
    if not loads:
        return
    labels = sorted(loads.keys())
    cpu = [loads[k][0] for k in labels]
    mem = [loads[k][1] for k in labels]
    bw = [loads[k][2] for k in labels]

    y_pos = list(range(len(labels)))
    bar_height = 0.25

    fig, ax = plt.subplots(figsize=(8, max(4, len(labels) * 0.4)))
    ax.barh([y + bar_height * 0 for y in y_pos], cpu, height=bar_height, color="#1f77b4", label="CPU")
    ax.barh([y + bar_height * 1 for y in y_pos], mem, height=bar_height, color="#2ca02c", label="Memory")
    ax.barh([y + bar_height * 2 for y in y_pos], bw, height=bar_height, color="#d62728", label="Bandwidth")

    ax.set_yticks([y + bar_height for y in y_pos])
    ax.set_yticklabels(labels)
    ax.set_xlim(0, 1)
    ax.set_xlabel("Normalized load")
    ax.set_title("Fog/Cloud load (latest snapshot)")
    ax.legend()
    ax.grid(axis="x", alpha=0.2)

    out = ROOT / "results" / "plots" / "load_bars.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    plt.tight_layout()
    plt.savefig(out, dpi=150)
    plt.close(fig)
    print(f"Saved {out}")


def load_at_time(history, now):
    """Get latest loads up to 'now' for each fog/cloud."""
    labels = sorted(history.keys())
    cpu = []
    mem = []
    bw = []
    for k in labels:
        samples = history.get(k, [])
        latest = (0, 0, 0)
        for t, c, m, b in samples:
            if t <= now and t >= latest[0]:
                latest = (t, c, m, b)
        cpu.append(latest[1])
        mem.append(latest[2])
        bw.append(latest[3])
    return cpu, mem, bw, labels


def resources_at_time(history, server_fog, now):
    """Return list of dicts with fog/server loads up to now."""
    rows = []
    for server, samples in history.items():
        latest = (0, 0, 0, 0, server_fog.get(server, ""))
        for t, c, m, b, fog in samples:
            if t <= now and t >= latest[0]:
                latest = (t, c, m, b, fog)
        rows.append({"server": server, "fog": latest[4], "cpu": latest[1], "mem": latest[2], "bw": latest[3]})
    rows.sort(key=lambda r: (r["fog"], r["server"]))
    return rows


def build_fault_windows(faults):
    """Map server -> list of (fail, recovery)."""
    windows = {}
    for f in faults:
        fail = f.get("failedAt", f.get("time", 0))
        recovery = f.get("recoveryAt", fail + 0.001)
        server = f.get("server")
        if server:
            windows.setdefault(server, []).append((fail, recovery))
    return windows


def failed_servers(windows, now):
    """Return set of servers failing at time now."""
    failed = set()
    for server, ranges in windows.items():
        for fail, rec in ranges:
            if fail <= now <= rec:
                failed.add(server)
                break
    return failed


if __name__ == "__main__":
    main()
