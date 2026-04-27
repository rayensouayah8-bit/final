package services;

import java.sql.SQLException;

public interface CRUD<T, K> {

    void create(T entity) throws SQLException;

    void insert(T entity) throws SQLException;

    void update(T entity) throws SQLException;

    void delete(K id) throws SQLException;
}
