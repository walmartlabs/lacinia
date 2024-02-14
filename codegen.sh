#!/bin/sh

set -x

pip install antlr4-tools

PACKAGE_NAME='com/walmartlabs/lacinia'

antlr4 resources/$PACKAGE_NAME/Graphql.g4 -o java/$PACKAGE_NAME -Xexact-output-dir -package com.walmartlabs.lacinia
antlr4 resources/$PACKAGE_NAME/GraphqlSchema.g4 -o java/$PACKAGE_NAME -Xexact-output-dir -package com.walmartlabs.lacinia
