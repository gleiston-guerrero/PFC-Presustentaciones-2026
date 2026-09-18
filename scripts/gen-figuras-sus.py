#!/usr/bin/env python3
"""Genera las figuras del SUS a partir de los CSV ya versionados.

Misma regla que gen-figuras.py: no inventa datos. Si falta un archivo de
entrada, la figura se salta con un aviso en vez de dibujar numeros falsos.

Entradas:
    docs/mediciones/sus/sus-respuestas.csv                        (ronda en papel)
    docs/mediciones/sus/re-aplicacion/sus-respuestas-formulario.csv (ronda 18-sep)
Salida:
    docs/mediciones/sus/figuras/*.png

Uso:  python scripts/gen-figuras-sus.py
"""
import csv
import os
import statistics as st
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUS = os.path.join(RAIZ, "docs", "mediciones", "sus")
PAPEL = os.path.join(SUS, "sus-respuestas.csv")
FORM = os.path.join(SUS, "re-aplicacion", "sus-respuestas-formulario.csv")
OUT = os.path.join(SUS, "figuras")

AZUL, VERDE, GRIS, ROJO = "#2c5f8a", "#12694a", "#8c96a0", "#a8322d"

ITEMS_CORTOS = [
    "1. Usaría con\nfrecuencia", "2. Innecesaria-\nmente complejo",
    "3. Fácil de usar", "4. Necesitaría\napoyo técnico",
    "5. Funciones bien\nintegradas", "6. Demasiada\ninconsistencia",
    "7. Se aprende\nrápido", "8. Pesado/\nincómodo",
    "9. Me sentí\nseguro", "10. Aprender mucho\nantes de usar",
]
# En SUS los impares son positivos y los pares negativos: en los pares, un
# valor ALTO es malo. Se marca en la figura para no leerla al reves.
POSITIVO = [True, False] * 5


def leer(ruta, col_score="sus_score"):
    if not os.path.isfile(ruta):
        print(f"  (falta {os.path.relpath(ruta, RAIZ)}, se salta)")
        return None
    with open(ruta, encoding="utf-8") as f:
        return list(csv.DictReader(f))


def ic95(x):
    if len(x) < 2:
        return 0.0
    try:
        from scipy import stats
        t = stats.t.ppf(0.975, df=len(x) - 1)
    except ImportError:
        t = 2.145
    return t * st.stdev(x) / len(x) ** 0.5


def fig_comparacion(papel, form):
    """Las tres mediciones con su intervalo de confianza."""
    p15 = [float(r["sus_score"]) for r in papel]
    p4 = [float(r["sus_score"]) for r in papel if r.get("fecha_verificable") == "si"]
    nf = [float(r["sus_score"]) for r in form]

    grupos = [
        ("Papel\n15 hojas\n(11 con fecha\nno verificable)", p15, GRIS),
        ("Papel\nsolo las 4 con\nfecha verificable", p4, GRIS),
        ("Formulario 18-sep\nmuestra nueva,\nhora de un tercero", nf, VERDE),
    ]
    fig, ax = plt.subplots(figsize=(8.2, 5))
    for i, (etq, datos, color) in enumerate(grupos):
        m = st.mean(datos)
        e = ic95(datos)
        ax.errorbar(i, m, yerr=e, fmt="o", color=color, markersize=11,
                    capsize=9, capthick=2, elinewidth=2, zorder=3)
        ax.annotate(f"{m:.1f}", (i, m), xytext=(-16, 0),
                    textcoords="offset points", fontsize=11, ha="right",
                    va="center", fontweight="bold", color=color)
        ax.scatter([i + 0.13] * len(datos), datos, s=16, color=color,
                   alpha=0.3, zorder=2)

    ax.axhline(68, color=ROJO, ls="--", lw=1.4, zorder=1)
    ax.annotate("68 — promedio de la industria (Bangor et al.)",
                (-0.42, 68), xytext=(0, 7), textcoords="offset points",
                ha="left", fontsize=9, color=ROJO)

    ax.set_xticks(range(len(grupos)))
    ax.set_xticklabels([g[0] for g in grupos], fontsize=9)
    ax.set_ylabel("Puntaje SUS (0–100)")
    ax.set_ylim(0, 100)
    ax.set_xlim(-0.55, 2.55)
    ax.set_title("Las tres mediciones coinciden dentro de su margen de error",
                 fontsize=12, fontweight="bold", pad=12)
    ax.grid(axis="y", alpha=0.25)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    fig.tight_layout()
    return fig, "fig-sus-comparacion-rondas.png"


def fig_items(form):
    """Promedio por ítem de la ronda nueva, marcando el sentido de cada uno."""
    prom = [st.mean([float(r[f"q{n}"]) for r in form]) for n in range(1, 11)]
    colores = [VERDE if p else AZUL for p in POSITIVO]

    fig, ax = plt.subplots(figsize=(10.5, 5.2))
    barras = ax.bar(range(10), prom, color=colores, width=0.66)
    for b, v in zip(barras, prom):
        ax.annotate(f"{v:.2f}", (b.get_x() + b.get_width() / 2, v),
                    xytext=(0, 3), textcoords="offset points",
                    ha="center", fontsize=9.5, fontweight="bold")
    ax.axhline(3, color=GRIS, ls=":", lw=1.2)
    ax.annotate("3 = neutral", (9.55, 3), xytext=(0, 4),
                textcoords="offset points", ha="right", fontsize=8.5, color=GRIS)
    ax.set_xticks(range(10))
    ax.set_xticklabels(ITEMS_CORTOS, fontsize=8)
    ax.set_ylabel("Promedio (escala 1–5)")
    ax.set_ylim(0, 5.5)
    ax.set_title(f"Promedio por ítem — formulario del 18-sep (n={len(form)})",
                 fontsize=12, fontweight="bold", pad=12)
    ax.grid(axis="y", alpha=0.25)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    manijas = [plt.Rectangle((0, 0), 1, 1, color=VERDE),
               plt.Rectangle((0, 0), 1, 1, color=AZUL)]
    ax.legend(manijas,
              ["Ítem positivo: más alto es mejor",
               "Ítem negativo: más alto es PEOR"],
              loc="upper right", fontsize=8.5, framealpha=0.95)
    fig.tight_layout()
    return fig, "fig-sus-por-item.png"


def fig_distribucion(form):
    """Reparto de los puntajes individuales."""
    sc = sorted(float(r["sus_score"]) for r in form)
    m = st.mean(sc)
    fig, ax = plt.subplots(figsize=(8.2, 4.6))
    ax.hist(sc, bins=range(0, 105, 10), color=VERDE, alpha=0.8,
            edgecolor="white", linewidth=1.2)
    ax.axvline(m, color=AZUL, lw=2)
    ax.annotate(f"media {m:.1f}", (m, ax.get_ylim()[1] * 0.92),
                xytext=(7, 0), textcoords="offset points",
                fontsize=10, fontweight="bold", color=AZUL)
    ax.axvline(68, color=ROJO, ls="--", lw=1.4)
    ax.annotate("68", (68, ax.get_ylim()[1] * 0.92), xytext=(5, 0),
                textcoords="offset points", fontsize=10, color=ROJO)
    ax.set_xlabel("Puntaje SUS individual")
    ax.set_ylabel("Número de participantes")
    ax.set_title(f"Distribución de los puntajes — 18-sep (n={len(sc)})",
                 fontsize=12, fontweight="bold", pad=12)
    ax.grid(axis="y", alpha=0.25)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    fig.tight_layout()
    return fig, "fig-sus-distribucion.png"


def fig_rol(form):
    """Puntaje por rol. Con estos tamaños no se comparan grupos: se describe."""
    por = {}
    for r in form:
        por.setdefault(r.get("rol", "—") or "—", []).append(float(r["sus_score"]))
    orden = sorted(por, key=lambda k: -len(por[k]))
    fig, ax = plt.subplots(figsize=(8.2, 4.6))
    for i, rol in enumerate(orden):
        v = por[rol]
        ax.scatter([i] * len(v), v, s=52, color=VERDE, alpha=0.62, zorder=3)
        ax.hlines(st.mean(v), i - 0.22, i + 0.22, color=AZUL, lw=2.4, zorder=4)
        ax.annotate(f"{st.mean(v):.1f}", (i + 0.26, st.mean(v)),
                    fontsize=9.5, color=AZUL, fontweight="bold", va="center")
    ax.axhline(68, color=ROJO, ls="--", lw=1.3)
    ax.set_xticks(range(len(orden)))
    ax.set_xticklabels([f"{r}\n(n={len(por[r])})" for r in orden], fontsize=9.5)
    ax.set_ylabel("Puntaje SUS")
    ax.set_ylim(0, 100)
    ax.set_title("Puntaje por rol — descriptivo, no comparativo",
                 fontsize=12, fontweight="bold", pad=12)
    ax.grid(axis="y", alpha=0.25)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    fig.text(0.5, 0.012,
             "Con 1 a 7 respuestas por rol no se sostiene una comparación entre grupos; "
             "se muestran los valores tal cual.",
             ha="center", fontsize=8, color=GRIS, style="italic")
    fig.tight_layout(rect=(0, 0.045, 1, 1))
    return fig, "fig-sus-por-rol.png"


def main():
    papel = leer(PAPEL)
    form = leer(FORM)
    if form is None:
        print("Sin la ronda del formulario no hay nada que graficar.")
        print("Corre antes: python scripts/sus-ingesta.py <csv exportado>")
        return 1
    os.makedirs(OUT, exist_ok=True)

    figuras = [fig_items(form), fig_distribucion(form), fig_rol(form)]
    if papel:
        figuras.insert(0, fig_comparacion(papel, form))

    for fig, nombre in figuras:
        ruta = os.path.join(OUT, nombre)
        fig.savefig(ruta, dpi=160, bbox_inches="tight")
        plt.close(fig)
        print(f"  {os.path.relpath(ruta, RAIZ)}")
    print(f"\n{len(figuras)} figuras generadas desde los CSV versionados.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
