#!/bin/sh

set -x

pip install antlr4-tools

export ANTLR4_TOOLS_ANTLR_VERSION=4.13.1

PACKAGE_NAME=com.walmartlabs.lacinia
DIR=$(echo $PACKAGE_NAME | tr . /)

antlr4 resources/$DIR/Graphql.g4 -o java/$DIR -Xexact-output-dir -package $PACKAGE_NAME
antlr4 resources/$DIR/GraphqlSchema.g4 -o java/$DIR -Xexact-output-dir -package $PACKAGE_NAME
