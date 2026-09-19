#!/bin/sh
# Arma el archivo .tar.gz que se sube a mano a Zenodo para archivar una version.
#
# POR QUE EXISTE
# --------------
# La integracion automatica GitHub -> Zenodo de este proyecto quedo apuntando a
# `carla22072004/PFC-Presustentaciones-2026`, una ruta que ya no existe bajo esa
# cuenta: el repositorio se transfirio a `gleiston-guerrero`. Reactivarla exigiria
# que el nuevo propietario vincule su cuenta de GitHub con Zenodo, algo que no
# depende del equipo.
#
# No hace falta. Zenodo publica igual con una subida manual, y el DOI que produce
# es exactamente el mismo tipo de DOI que produciria la integracion. Lo unico que
# cambia es quien sube el archivo.
#
# QUE GARANTIZA
# -------------
# El paquete sale de `git archive` sobre un TAG, no del directorio de trabajo: lo
# que se archiva es exactamente el commit etiquetado, sin archivos sin commitear,
# sin `target/`, sin `node_modules/`. Si el tag se mueve, hay que volver a correr
# esto y subir una version nueva.
#
# Uso:  sh scripts/zenodo-paquete.sh [tag]     (por defecto, v1.1.0)
set -e

TAG="${1:-v1.1.0}"
SALIDA="${2:-.}"

if ! git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "ERROR: el tag '$TAG' no existe en este repositorio."
  echo "Tags disponibles:"
  git tag -l | sed 's/^/   /'
  exit 1
fi

COMMIT=$(git rev-list -n1 "$TAG")
CORTO=$(git rev-parse --short "$COMMIT")
FECHA=$(git log -1 --format=%cd --date=short "$COMMIT")
NOMBRE="PFC-Presustentaciones-2026-${TAG}"
PAQUETE="${SALIDA}/${NOMBRE}.tar.gz"

echo "Empaquetando el tag $TAG"
echo "   commit: $CORTO   fecha: $FECHA"

git archive --format=tar.gz --prefix="${NOMBRE}/" -o "$PAQUETE" "$TAG"

BYTES=$(wc -c < "$PAQUETE")
MB=$(awk "BEGIN{printf \"%.1f\", $BYTES/1048576}")
ARCHIVOS=$(git ls-tree -r --name-only "$TAG" | wc -l)

echo ""
echo "Listo: $PAQUETE"
echo "   ${MB} MB, ${ARCHIVOS} archivos, todos del commit ${CORTO}"
echo ""
echo "Metadatos para el formulario de Zenodo (copiar tal cual):"
echo "   Title           Sistema de Gestion de Pre-Sustentaciones de Titulacion UTEQ"
echo "   Version         ${TAG}"
echo "   Publication date ${FECHA}"
echo "   Resource type   Software"
echo "   License         MIT"
echo ""
echo "Siguiente paso: docs/ZENODO.md, seccion 'Como archivar una version nueva'."
