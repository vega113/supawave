package com.google.gwt.core.client;

public final class GWT {
  private GWT() {
  }

  public static <T> T create(Class<?> classLiteral) {
    try {
      @SuppressWarnings("unchecked")
      T instance = (T) classLiteral.getDeclaredConstructor().newInstance();
      return instance;
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean isClient() {
    return false;
  }
}
