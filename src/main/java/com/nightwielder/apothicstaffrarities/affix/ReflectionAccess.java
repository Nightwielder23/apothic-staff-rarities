package com.nightwielder.apothicstaffrarities.affix;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// FG&A exposes no public mutator for an affix's per-rarity values, so they are read and rewritten by reflection.
final class ReflectionAccess {

    private ReflectionAccess() {}

    static Field findFieldUp(final Class<?> startingClass, final String fieldName) {
        Class<?> current = startingClass;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (final NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    static Object getField(final Field field, final Object target) throws IllegalAccessException {
        field.setAccessible(true);
        return field.get(target);
    }

    static Object invokeAccessor(final Object target, final String methodName) throws Exception {
        final Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    static Object constructInstance(final Class<?> cls, final Class<?>[] parameterTypes, final Object... arguments) throws Exception {
        final Constructor<?> constructor = cls.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    // The values field is final; the write relies on running on the server thread before any read observes it.
    static void setField(final Field field, final Object target, final Object value) throws IllegalAccessException {
        field.setAccessible(true);
        field.set(target, value);
    }
}
