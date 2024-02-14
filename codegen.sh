#!/bin/sh

set -x

pip install antlr4-tools
antlr4 java/com/walmartlabs/lacinia/Graphql.g4 -package com.walmartlabs.lacinia
antlr4 java/com/walmartlabs/lacinia/GraphqlSchema.g4 -package com.walmartlabs.lacinia