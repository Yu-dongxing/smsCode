package com.wzz.smscode.service;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wzz.smscode.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DatabaseInitService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void initDatabase() throws SQLException {
        List<Class<?>> entityClasses = scanEntityClasses("com.wzz.smscode.entity");

        for (Class<?> entityClass : entityClasses) {
            TableName tableNameAnnotation = entityClass.getAnnotation(TableName.class);
            if (tableNameAnnotation != null) {
                String tableName = tableNameAnnotation.value();
                createOrUpdateTable(entityClass, tableName);
            }
        }
    }

    private List<Class<?>> scanEntityClasses(String basePackage) {
        // ... 此方法无需改动 ...
        List<Class<?>> entityClasses = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        for (var beanDef : scanner.findCandidateComponents(basePackage)) {
            try {
                Class<?> entityClass = Class.forName(beanDef.getBeanClassName());
                entityClasses.add(entityClass);
            } catch (ClassNotFoundException e) {
                System.err.println("Class not found: " + beanDef.getBeanClassName());
                e.printStackTrace();
            }
        }
        return entityClasses;
    }


    private boolean tableExists(String tableName) {
        try {
            // 使用 information_schema 进行精确查询，避免大小写和 schema 带来的问题
            // DATABASE() 函数会返回当前连接的数据库名，确保只在当前库中查找
            String sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";

            // 使用 jdbcTemplate 查询，更符合Spring的最佳实践
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);

            return count != null && count > 0;
        } catch (Exception e) {
            System.err.println("Error checking if table " + tableName + " exists: " + e.getMessage());
            // 在检查阶段发生错误时，保守地认为表不存在，以便尝试创建
            return false;
        }
    }


    private void createOrUpdateTable(Class<?> entityClass, String tableName) throws SQLException {
        if (!tableExists(tableName)) {
            createTable(entityClass, tableName);
        } else {
            updateTable(entityClass, tableName);
        }
    }

    private void createTable(Class<?> entityClass, String tableName) {
        try {
            StringBuilder createTableSQL = new StringBuilder("CREATE TABLE ");
            createTableSQL.append(tableName).append(" (");

            // ▼▼▼ 修改点：使用 getAllFields 替代 getDeclaredFields ▼▼▼
            List<Field> fields = getAllFields(entityClass);
            List<String> definitions = new ArrayList<>();
            boolean hasPrimaryKey = false;

            for (Field field : fields) {
                // 跳过静态字段或 Mybatis-Plus 的 a `serialVersionUID` 字段
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || "serialVersionUID".equals(field.getName())) {
                    continue;
                }

                TableId tableId = field.getAnnotation(TableId.class);
                TableField tableField = field.getAnnotation(TableField.class);

                String columnName = null;
                String dataType = null;
                String defaultValueSql = getDefaultValueSql(field);
                String commentSql = getColumnCommentSql(field); // ▼▼▼ 新增：获取字段注释SQL ▼▼▼

                if (tableId != null) {
                    columnName = tableId.value().isEmpty() ? field.getName() : tableId.value();
                    dataType = getDataType(field);
                    definitions.add(columnName + " " + dataType + " PRIMARY KEY AUTO_INCREMENT" + defaultValueSql + commentSql);
                    hasPrimaryKey = true;
                } else if (tableField != null && tableField.exist()) {
                    columnName = tableField.value().isEmpty() ? field.getName() : tableField.value();
                    dataType = getDataType(field);
                    definitions.add(columnName + " " + dataType + defaultValueSql + commentSql);
                }
            }

            if (!hasPrimaryKey) {
                definitions.add(0, "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID'");
            }

            // ▼▼▼ 新增：处理索引 ▼▼▼
            List<String> indexDefinitions = getIndexDefinitionsSQL(entityClass);
            definitions.addAll(indexDefinitions);

            // ▼▼▼ 新增：处理外键 ▼▼▼
            List<String> foreignKeyDefinitions = getForeignKeyDefinitionsSQL(entityClass);
            definitions.addAll(foreignKeyDefinitions);

            createTableSQL.append(String.join(", ", definitions)).append(")");

            // ▼▼▼ 新增：处理表注释 ▼▼▼
            createTableSQL.append(getTableCommentSql(entityClass));

            createTableSQL.append(";");

            System.out.println("Executing SQL: " + createTableSQL);
            jdbcTemplate.execute(createTableSQL.toString());
            System.out.println("Created table " + tableName);

        } catch (Exception e) {
            System.err.println("Error creating table " + tableName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }


    // ▼▼▼ 【优化版】的 updateTable 方法 ▼▼▼
    private void updateTable(Class<?> entityClass, String tableName) throws SQLException {
        try {
            // 1. 批量获取数据库中已存在的元数据
            Set<String> existingColumns = getExistingColumns(tableName);
            Set<String> existingIndexes = getExistingIndexNames(tableName);
            Set<String> existingForeignKeys = getExistingForeignKeyNames(tableName);

            // 2. 检查并添加新列
            List<Field> fields = getAllFields(entityClass);
            for (Field field : fields) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || "serialVersionUID".equals(field.getName())) {
                    continue;
                }

                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && tableField.exist()) {
                    String columnName = tableField.value().isEmpty() ? field.getName() : tableField.value();

                    // 在内存中检查，而不是查询数据库
                    if (!existingColumns.contains(columnName.toLowerCase())) { // 注意：数据库元数据可能返回全小写或大写，统一转小写比较保险
                        String dataType = getDataType(field);
                        String defaultValueSql = getDefaultValueSql(field);
                        String commentSql = getColumnCommentSql(field);
                        String addColumnSQL = String.format("ALTER TABLE %s ADD COLUMN %s %s %s %s;",
                                tableName, columnName, dataType, defaultValueSql, commentSql);
                        try {
                            System.out.println("Executing SQL: " + addColumnSQL);
                            jdbcTemplate.execute(addColumnSQL);
                            System.out.println("Added column " + columnName + " to table " + tableName);
                        } catch (Exception e) {
                            System.err.println("Failed to add column " + columnName + " to table " + tableName + ": " + e.getMessage());
                        }
                    }
                }
            }

            // 3. 检查并添加新索引
            Index[] indexes = entityClass.getAnnotationsByType(Index.class);
            for (Index index : indexes) {
                // 在内存中检查
                if (!existingIndexes.contains(index.name().toLowerCase())) {
                    String addIndexSql = "ALTER TABLE " + tableName + " ADD " + buildIndexDefinition(index) + ";";
                    try {
                        System.out.println("Executing SQL: " + addIndexSql);
                        jdbcTemplate.execute(addIndexSql);
                        System.out.println("Added index " + index.name() + " to table " + tableName);
                    } catch (Exception e) {
                        System.err.println("Failed to add index " + index.name() + " to table " + tableName + ": " + e.getMessage());
                    }
                }
            }

            // 4. 检查并添加新外键
            ForeignKey[] foreignKeys = entityClass.getAnnotationsByType(ForeignKey.class);
            for (ForeignKey fk : foreignKeys) {
                // 在内存中检查
                if (!existingForeignKeys.contains(fk.name().toLowerCase())) {
                    String addFkSql = "ALTER TABLE " + tableName + " ADD " + buildForeignKeyDefinition(fk) + ";";
                    try {
                        System.out.println("Executing SQL: " + addFkSql);
                        jdbcTemplate.execute(addFkSql);
                        System.out.println("Added foreign key " + fk.name() + " to table " + tableName);
                    } catch (Exception e) {
                        System.err.println("Failed to add foreign key " + fk.name() + " to table " + tableName + ": " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error updating table " + tableName + ": " + e.getMessage());
            throw e;
        }
    }


// ▼▼▼ 新增的辅助方法：用于批量获取元数据 ▼▼▼

    /**
     * 批量获取一个表的所有列名
     */
    private Set<String> getExistingColumns(String tableName) {
        // 使用 information_schema 效率更高
        String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        List<String> columnList = jdbcTemplate.queryForList(sql, String.class, tableName);
        // 转为小写并存入Set，便于快速、不区分大小写地查找
        return columnList.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    /**
     * 批量获取一个表的所有索引名
     */
    private Set<String> getExistingIndexNames(String tableName) {
        // 排除主键索引 (PRIMARY)
        String sql = "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME != 'PRIMARY'";
        List<String> indexList = jdbcTemplate.queryForList(sql, String.class, tableName);
        return indexList.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    /**
     * 批量获取一个表的所有外键约束名
     */
    private Set<String> getExistingForeignKeyNames(String tableName) {
        String sql = "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'FOREIGN KEY'";
        List<String> fkList = jdbcTemplate.queryForList(sql, String.class, tableName);
        return fkList.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        // ... 此方法无需改动 ...
        try {
            DatabaseMetaData metaData = jdbcTemplate.getDataSource().getConnection().getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, tableName, columnName)) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("Error checking if column " + columnName + " exists in table " + tableName + ": " + e.getMessage());
            throw e;
        }
    }

    // ▼▼▼ 新增方法：检查索引是否存在 ▼▼▼
    private boolean indexExists(String tableName, String indexName) {
        try {
            // 使用 information_schema 查询，更通用和准确
            // DATABASE() 是 MySQL/MariaDB 的函数，用于获取当前数据库名
            String sql = "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, indexName);
            return count != null && count > 0;
        } catch (Exception e) {
            System.err.println("Error checking if index " + indexName + " exists in table " + tableName + ": " + e.getMessage());
            // 发生错误时，保守地返回true，避免重复创建导致失败
            return true;
        }
    }

    private String getDefaultValueSql(Field field) {
        // ... 此方法无需改动 ...
        if (field.isAnnotationPresent(DefaultValue.class)) {
            DefaultValue defaultValueAnnotation = field.getAnnotation(DefaultValue.class);
            return " DEFAULT " + defaultValueAnnotation.value();
        }
        return "";
    }

    // ▼▼▼ 新增方法：从注解获取字段注释SQL ▼▼▼
    private String getColumnCommentSql(Field field) {
        if (field.isAnnotationPresent(ColumnComment.class)) {
            return " COMMENT '" + field.getAnnotation(ColumnComment.class).value() + "'";
        }
        return "";
    }

    // ▼▼▼ 新增方法：从注解获取表注释SQL ▼▼▼
    private String getTableCommentSql(Class<?> entityClass) {
        if (entityClass.isAnnotationPresent(TableComment.class)) {
            return " COMMENT='" + entityClass.getAnnotation(TableComment.class).value() + "'";
        }
        return "";
    }

    // ▼▼▼ 新增方法：从注解获取所有索引定义SQL（用于CREATE TABLE）▼▼▼
    private List<String> getIndexDefinitionsSQL(Class<?> entityClass) {
        List<String> definitions = new ArrayList<>();
        // getAnnotationsByType 可以直接获取所有重复的注解，无需关心容器注解
        Index[] indexes = entityClass.getAnnotationsByType(Index.class);
        for (Index index : indexes) {
            definitions.add(buildIndexDefinition(index));
        }
        return definitions;
    }

    // ▼▼▼ 新增方法：构建单条索引定义的SQL字符串 ▼▼▼
    private String buildIndexDefinition(Index index) {
        StringBuilder sb = new StringBuilder();
        switch (index.type()) {
            case UNIQUE:
                sb.append("UNIQUE KEY ");
                break;
            case FULLTEXT:
                sb.append("FULLTEXT KEY ");
                break;
            default: // NORMAL
                sb.append("KEY ");
                break;
        }

        // 拼接索引名和字段
        sb.append("`").append(index.name()).append("`");
        sb.append(" (")
                .append("`").append(String.join("`,`", index.columns())).append("`")
                .append(")");

        // 添加索引方法 (例如 USING BTREE)
        sb.append(" USING BTREE");

        // 添加索引注释
        if (index.comment() != null && !index.comment().isEmpty()) {
            sb.append(" COMMENT '").append(index.comment()).append("'");
        }
        return sb.toString();
    }


    private String getDataType(Field field) {
        // ... 此方法无需改动 ...
        if (field.isAnnotationPresent(ColumnType.class)) {
            ColumnType columnType = field.getAnnotation(ColumnType.class);
            return columnType.value();
        }
        Class<?> fieldType = field.getType();
        if (fieldType.equals(String.class)) { return "VARCHAR(255)"; }
        else if (fieldType.equals(LocalDateTime.class)) { return "DATETIME"; }
        else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) { return "BIGINT"; }
        else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) { return "INT"; }
        else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) { return "DOUBLE"; }
        else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) { return "FLOAT"; }
        else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) { return "TINYINT(1)"; }
        else if (fieldType.equals(Date.class)) { return "DATETIME"; }
        else if (fieldType.equals(java.sql.Timestamp.class)) { return "TIMESTAMP"; }
        else if (fieldType.equals(BigDecimal.class)) { return "DECIMAL(19,2)"; }
        else if (fieldType.equals(Byte.class) || fieldType.equals(byte.class)) { return "TINYINT"; }
        else if (fieldType.equals(Short.class) || fieldType.equals(short.class)) { return "SMALLINT"; }
        else if (Collection.class.isAssignableFrom(fieldType)) { return "JSON"; }
        else if (Map.class.isAssignableFrom(fieldType)) { return "JSON"; }
        else { return "VARCHAR(255)"; }
    }


    // ▼▼▼ 新增辅助方法 ▼▼▼

    /**
     * 检查外键是否存在
     */
    private boolean foreignKeyExists(String tableName, String fkName) {
        try {
            String sql = "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ? AND CONSTRAINT_TYPE = 'FOREIGN KEY'";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, fkName);
            return count != null && count > 0;
        } catch (Exception e) {
            System.err.println("Error checking if foreign key " + fkName + " exists in table " + tableName + ": " + e.getMessage());
            return true; // 保守返回true，避免重复创建
        }
    }

    /**
     * 从注解获取所有外键定义SQL（用于CREATE TABLE）
     */
    private List<String> getForeignKeyDefinitionsSQL(Class<?> entityClass) {
        List<String> definitions = new ArrayList<>();
        ForeignKey[] foreignKeys = entityClass.getAnnotationsByType(ForeignKey.class);
        for (ForeignKey fk : foreignKeys) {
            definitions.add(buildForeignKeyDefinition(fk));
        }
        return definitions;
    }

    /**
     * ▼▼▼ 新增方法：递归获取一个类及其所有父类的所有字段 ▼▼▼
     *
     * @param clazz 要获取字段的类
     * @return 包含所有字段的列表
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        // 当父类为 null 或 Object.class 时停止
        while (clazz != null && !clazz.equals(Object.class)) {
            // 将当前类的所有声明字段添加到列表中
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            // 获取父类，继续下一次循环
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    /**
     * 构建单条外键定义的SQL字符串
     */
    private String buildForeignKeyDefinition(ForeignKey fk) {
        // 获取引用的表名
        TableName referencedTableNameAnn = fk.referenceEntity().getAnnotation(TableName.class);
        if (referencedTableNameAnn == null) {
            throw new IllegalArgumentException("The referenceEntity " + fk.referenceEntity().getSimpleName() + " must have a @TableName annotation.");
        }
        String referencedTableName = referencedTableNameAnn.value();

        String columnsSql = "`" + String.join("`,`", fk.columns()) + "`";
        String referencedColumnsSql = "`" + String.join("`,`", fk.referencedColumns()) + "`";

        // ON DELETE 和 ON UPDATE 的SQL片段
        String onDeleteSql = "ON DELETE " + fk.onDelete().name().replace('_', ' ');
        String onUpdateSql = "ON UPDATE " + fk.onUpdate().name().replace('_', ' ');

        return String.format("CONSTRAINT `%s` FOREIGN KEY (%s) REFERENCES `%s` (%s) %s %s",
                fk.name(),
                columnsSql,
                referencedTableName,
                referencedColumnsSql,
                onDeleteSql,
                onUpdateSql
        );
    }

}