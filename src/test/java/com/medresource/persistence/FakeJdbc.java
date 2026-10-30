package com.medresource.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Conexiones falsas para probar el acceso a datos sin una base de datos real.
 *
 * <p>No es una prueba: solo registra qué sentencias se ejecutan y qué se cierra, y puede
 * simular un fallo de la base de datos.</p>
 */
final class FakeJdbc {

    /** Lo que ocurrió con las conexiones falsas. */
    static final class Recorder {

        final List<String> executed = new ArrayList<>();
        int connectionsClosed;
        int statementsClosed;

        /** Si no es {@code null}, ejecutar una sentencia que contenga este texto lanza un error. */
        String failWhenContaining;
    }

    private FakeJdbc() {
    }

    /** Crea una conexión falsa que anota todo en el registro. */
    static Connection connection(Recorder recorder) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "createStatement" -> statement(recorder);
            case "close" -> {
                recorder.connectionsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (Connection) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    private static Statement statement(Recorder recorder) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "execute" -> {
                String sql = (String) args[0];
                if (recorder.failWhenContaining != null && sql.contains(recorder.failWhenContaining)) {
                    throw new SQLException("fallo simulado");
                }
                recorder.executed.add(sql);
                yield false;
            }
            case "close" -> {
                recorder.statementsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (Statement) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                new Class<?>[]{Statement.class}, handler);
    }
}