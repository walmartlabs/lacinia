package com.walmartlabs.lacinia;

import clojure.lang.IDeref;

public class ParseError extends RuntimeException implements IDeref {
  public final Object errors;
  public final Object tree;

  public ParseError(final Object errors, final Object tree, final String msg) {
    super(msg);
    this.errors = errors;
    this.tree = tree;
  }

  public Object deref() {
    return errors;
  }
}
