/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.sql.jdbc;

import java.io.IOException;
import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.nuxeo.common.utils.StringUtils;
import org.nuxeo.ecm.core.storage.StorageException;
import org.nuxeo.ecm.core.storage.sql.ColumnType;
import org.nuxeo.ecm.core.storage.sql.Mapper;
import org.nuxeo.ecm.core.storage.sql.Model;
import org.nuxeo.ecm.core.storage.sql.ModelFulltext;
import org.nuxeo.ecm.core.storage.sql.Selection;
import org.nuxeo.ecm.core.storage.sql.SelectionType;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.Column;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.Database;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.Delete;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.Insert;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.Select;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.Table;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.Update;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.Table.IndexType;
import org.nuxeo.ecm.core.storage.sql.jdbc.dialect.Dialect;
import org.nuxeo.ecm.core.storage.sql.jdbc.dialect.SQLStatement;

/**
 * This singleton generates and holds the actual SQL DDL and DML statements for
 * the operations needed by the {@link Mapper}, given a {@link Model}.
 * <p>
 * It is specific to one SQL dialect.
 */
public class SQLInfo {

    private static final String ORDER_DESC = "DESC";

    private static final String ORDER_ASC = "ASC";

    public final Database database;

    public final Dialect dialect;

    private final Model model;

    private String selectRootIdSql;

    private Column selectRootIdWhatColumn;

    private final Map<String, String> insertSqlMap; // statement

    private final Map<String, List<Column>> insertColumnsMap;

    private final Map<String, String> deleteSqlMap; // statement

    private Map<SelectionType, SQLInfoSelection> selections;

    private String selectChildrenIdsAndTypesSql;

    private String selectComplexChildrenIdsAndTypesSql;

    private List<Column> selectChildrenIdsAndTypesWhatColumns;

    private String selectDescendantsInfoSql;

    private List<Column> selectDescendantsInfoWhatColumns;

    private final Map<String, String> copySqlMap;

    private final Map<String, Column> copyIdColumnMap;

    protected final Map<String, SQLInfoSelect> selectFragmentById;

    protected List<Column> clusterInvalidationsColumns;

    protected Map<String, List<SQLStatement>> sqlStatements;

    protected Map<String, Serializable> sqlStatementsProperties;

    protected List<String> getBinariesSql;

    protected List<Column> getBinariesColumns;

    /**
     * Generates and holds the needed SQL statements given a {@link Model} and a
     * {@link Dialect}.
     *
     * @param model the model
     * @param dialect the SQL dialect
     */
    public SQLInfo(Model model, Dialect dialect) throws StorageException {
        this.model = model;
        this.dialect = dialect;

        database = new Database(dialect);

        selectRootIdSql = null;
        selectRootIdWhatColumn = null;

        selectFragmentById = new HashMap<String, SQLInfoSelect>();

        selections = new HashMap<SelectionType, SQLInfoSelection>();

        selectChildrenIdsAndTypesSql = null;
        selectChildrenIdsAndTypesWhatColumns = null;
        selectComplexChildrenIdsAndTypesSql = null;

        insertSqlMap = new HashMap<String, String>();
        insertColumnsMap = new HashMap<String, List<Column>>();

        deleteSqlMap = new HashMap<String, String>();

        copySqlMap = new HashMap<String, String>();
        copyIdColumnMap = new HashMap<String, Column>();

        getBinariesSql = new ArrayList<String>(1);
        getBinariesColumns = new ArrayList<Column>(1);

        initSQL();
        initSelections();

        try {
            initSQLStatements(JDBCMapper.testProps);
        } catch (IOException e) {
            throw new StorageException(e);
        }
    }

    public Database getDatabase() {
        return database;
    }

    // ----- select -----

    public String getSelectRootIdSql() {
        return selectRootIdSql;
    }

    public Column getSelectRootIdWhatColumn() {
        return selectRootIdWhatColumn;
    }

    public String getInsertRootIdSql() {
        return insertSqlMap.get(model.REPOINFO_TABLE_NAME);
    }

    public List<Column> getInsertRootIdColumns() {
        return insertColumnsMap.get(model.REPOINFO_TABLE_NAME);
    }

    public SQLInfoSelection getSelection(SelectionType type) {
        return selections.get(type);

    }

    public String getSelectChildrenIdsAndTypesSql(boolean onlyComplex) {
        return onlyComplex ? selectComplexChildrenIdsAndTypesSql
                : selectChildrenIdsAndTypesSql;
    }

    public List<Column> getSelectChildrenIdsAndTypesWhatColumns() {
        return selectChildrenIdsAndTypesWhatColumns;
    }

    public String getSelectDescendantsInfoSql() {
        return selectDescendantsInfoSql;
    }

    public List<Column> getSelectDescendantsInfoWhatColumns() {
        return selectDescendantsInfoWhatColumns;
    }

    // ----- cluster -----

    public List<Column> getClusterInvalidationsColumns() {
        return clusterInvalidationsColumns;
    }

    // ----- insert -----

    /**
     * Returns the SQL {@code INSERT} to add a row. The columns that represent
     * sequences that are implicitly auto-incremented aren't included.
     *
     * @param tableName the table name
     * @return the SQL {@code INSERT} statement
     */
    public String getInsertSql(String tableName) {
        return insertSqlMap.get(tableName);
    }

    /**
     * Returns the list of columns to use for an {@INSERT} statement
     * {@link #getInsertSql}.
     *
     * @param tableName the table name
     * @return the list of columns
     */
    public List<Column> getInsertColumns(String tableName) {
        return insertColumnsMap.get(tableName);
    }

    // ----- update -----

    // TODO these two methods are redundant with one another

    public SQLInfoSelect getUpdateById(String tableName, Collection<String> keys) {
        Table table = database.getTable(tableName);
        Column mainColumn = table.getColumn(model.MAIN_KEY);
        List<Column> columns = new LinkedList<Column>();
        for (String key : keys) {
            columns.add(table.getColumn(key));
        }
        Update update = new Update(table);
        update.setUpdatedColumns(columns);
        update.setWhere(mainColumn.getQuotedName() + " = ?");
        columns.add(mainColumn);
        return new SQLInfoSelect(update.getStatement(), columns, null, null);
    }

    public Update getUpdateByIdForKeys(String tableName, List<String> keys) {
        Table table = database.getTable(tableName);
        Column mainColumn = table.getColumn(model.MAIN_KEY);
        List<Column> columns = new LinkedList<Column>();
        for (String key : keys) {
            columns.add(table.getColumn(key));
        }
        Update update = new Update(table);
        update.setUpdatedColumns(columns);
        update.setWhere(mainColumn.getQuotedName() + " = ?");
        return update;
    }

    /**
     * Select by ids for all values of several fragments.
     */
    public SQLInfoSelect getSelectFragmentsByIds(String tableName, int nids) {
        return getSelectFragmentsByIds(tableName, nids, null, null);
    }

    /**
     * Select by ids for all values of several fragments (maybe ordered along
     * columns -- for collection fragments retrieval).
     */
    public SQLInfoSelect getSelectFragmentsByIds(String tableName, int nids,
            String[] orderBys, Set<String> skipColumns) {
        Table table = database.getTable(tableName);
        List<Column> whatColumns = new LinkedList<Column>();
        List<String> whats = new LinkedList<String>();
        List<Column> opaqueColumns = new LinkedList<Column>();
        for (Column column : table.getColumns()) {
            if (column.isOpaque()) {
                opaqueColumns.add(column);
            } else if (skipColumns == null
                    || !skipColumns.contains(column.getKey())) {
                whatColumns.add(column);
                whats.add(column.getQuotedName());
            }
        }
        Column whereColumn = table.getColumn(model.MAIN_KEY);
        StringBuilder wherebuf = new StringBuilder(whereColumn.getQuotedName());
        wherebuf.append(" IN (");
        for (int i = 0; i < nids; i++) {
            if (i != 0) {
                wherebuf.append(", ");
            }
            wherebuf.append('?');
        }
        wherebuf.append(')');
        Select select = new Select(table);
        select.setWhat(StringUtils.join(whats, ", "));
        select.setFrom(table.getQuotedName());
        select.setWhere(wherebuf.toString());
        if (orderBys != null) {
            List<String> orders = new LinkedList<String>();
            for (String orderBy : orderBys) {
                orders.add(table.getColumn(orderBy).getQuotedName());
            }
            select.setOrderBy(StringUtils.join(orders, ", "));
        }
        return new SQLInfoSelect(select.getStatement(), whatColumns,
                Collections.singletonList(whereColumn),
                opaqueColumns.isEmpty() ? null : opaqueColumns);
    }

    /**
     * Select all ancestors ids for several fragments.
     * <p>
     * Fast alternative to the slowest iterative
     * {@link #getSelectParentIds}.
     *
     * @return null if it's not possible in one call in this dialect
     */
    public SQLInfoSelect getSelectAncestorsIds() {
        String sql = dialect.getAncestorsIdsSql();
        if (sql == null) {
            return null;
        }
        Table table = database.getTable(model.HIER_TABLE_NAME);
        Column mainColumn = table.getColumn(model.MAIN_KEY);
        return new SQLInfoSelect(sql, Collections.singletonList(mainColumn),
                null, null);
    }

    /**
     * Select parentid by ids for all values of several fragments.
     */
    public SQLInfoSelect getSelectParentIds(int nids) {
        Table table = database.getTable(model.HIER_TABLE_NAME);
        Column whatColumn = table.getColumn(model.HIER_PARENT_KEY);
        Column whereColumn = table.getColumn(model.MAIN_KEY);
        StringBuilder wherebuf = new StringBuilder(whereColumn.getQuotedName());
        wherebuf.append(" IN (");
        for (int i = 0; i < nids; i++) {
            if (i != 0) {
                wherebuf.append(", ");
            }
            wherebuf.append('?');
        }
        wherebuf.append(')');
        Select select = new Select(table);
        select.setWhat("DISTINCT " + whatColumn.getQuotedName());
        select.setFrom(table.getQuotedName());
        select.setWhere(wherebuf.toString());
        return new SQLInfoSelect(select.getStatement(),
                Collections.singletonList(whatColumn),
                Collections.singletonList(whereColumn), null);
    }

    // ----- delete -----

    /**
     * Returns the SQL {@code DELETE} to delete a row. The primary key columns
     * are free parameters.
     *
     * @param tableName the table name
     * @return the SQL {@code DELETE} statement
     */
    public String getDeleteSql(String tableName) {
        return deleteSqlMap.get(tableName);
    }

    /**
     * Returns the SQL {@code DELETE} to delete several rows. The primary key
     * columns are free parameters.
     *
     * @param tableName the table name
     * @param n the number of rows to delete
     * @return the SQL {@code DELETE} statement with a {@code IN} for the keys
     */
    public String getDeleteSql(String tableName, int n) {
        Table table = database.getTable(tableName);
        Delete delete = new Delete(table);
        String where = null;
        for (Column column : table.getColumns()) {
            if (column.getKey().equals(model.MAIN_KEY)) {
                StringBuilder buf = new StringBuilder();
                buf.append(column.getQuotedName());
                if (n == 1) {
                    buf.append(" = ?");
                } else {
                    buf.append(" IN (");
                    for (int i = 0; i < n; i++) {
                        if (i > 0) {
                            buf.append(", ");
                        }
                        buf.append("?");
                    }
                    buf.append(")");
                }
                where = buf.toString();
            }
        }
        delete.setWhere(where);
        return delete.getStatement();
    }

    // ----- copy -----

    public SQLInfoSelect getCopyHier(boolean explicitName, boolean resetVersion) {
        Table table = database.getTable(model.HIER_TABLE_NAME);
        Collection<Column> columns = table.getColumns();
        List<String> selectWhats = new ArrayList<String>(columns.size());
        List<Column> selectWhatColumns = new ArrayList<Column>(5);
        Insert insert = new Insert(table);
        for (Column column : columns) {
            if (column.isIdentity()) {
                // identity column is never copied
                continue;
            }
            insert.addColumn(column);
            String quotedName = column.getQuotedName();
            String key = column.getKey();
            if (key.equals(model.MAIN_KEY) //
                    || key.equals(model.HIER_PARENT_KEY) //
                    || key.equals(model.MAIN_BASE_VERSION_KEY) //
                    || key.equals(model.MAIN_CHECKED_IN_KEY) //
                    || (key.equals(model.MAIN_MINOR_VERSION_KEY) && resetVersion) //
                    || (key.equals(model.MAIN_MAJOR_VERSION_KEY) && resetVersion) //
                    || (key.equals(model.HIER_CHILD_NAME_KEY) && explicitName)) {
                // explicit value set
                selectWhats.add("?");
                selectWhatColumns.add(column);
            } else {
                // otherwise copy value
                selectWhats.add(quotedName);
            }
        }
        Column whereColumn = table.getColumn(model.MAIN_KEY);
        Select select = new Select(null);
        select.setFrom(table.getQuotedName());
        select.setWhat(StringUtils.join(selectWhats, ", "));
        select.setWhere(whereColumn.getQuotedName() + " = ?");
        insert.setValues(select.getStatement());
        String sql = insert.getStatement();
        return new SQLInfoSelect(sql, selectWhatColumns,
                Collections.singletonList(whereColumn), null);
    }

    public String getCopySql(String tableName) {
        return copySqlMap.get(tableName);
    }

    public Column getCopyIdColumn(String tableName) {
        return copyIdColumnMap.get(tableName);
    }

    // ----- prepare everything -----

    /**
     * Creates all the sql from the models.
     */
    protected void initSQL() throws StorageException {

        // structural tables
        if (model.getRepositoryDescriptor().clusteringEnabled) {
            if (!dialect.isClusteringSupported()) {
                throw new StorageException("Clustering not supported for "
                        + dialect.getClass().getSimpleName());
            }
            initClusterSQL();
        }
        initHierarchySQL();
        initRepositorySQL();
        if (dialect.supportsAncestorsTable()) {
            initAncestorsSQL();
        }

        for (String tableName : model.getFragmentNames()) {
            if (tableName.equals(model.HIER_TABLE_NAME)) {
                continue;
            }
            initFragmentSQL(tableName);
        }

        /*
         * versions
         */

        Table hierTable = database.getTable(model.HIER_TABLE_NAME);
        Table versionTable = database.getTable(model.VERSION_TABLE_NAME);
        hierTable.addIndex(model.MAIN_IS_VERSION_KEY);
        versionTable.addIndex(model.VERSION_VERSIONABLE_KEY);
        // don't index series+label, a simple label scan will suffice

        /*
         * proxies
         */

        Table proxyTable = database.getTable(model.PROXY_TABLE_NAME);
        proxyTable.addIndex(model.PROXY_VERSIONABLE_KEY);
        proxyTable.addIndex(model.PROXY_TARGET_KEY);

        initSelectDescendantsSQL();

        /*
         * fulltext
         */
        if (!model.getRepositoryDescriptor().fulltextDisabled) {
            Table table = database.getTable(model.FULLTEXT_TABLE_NAME);
            ModelFulltext fulltextInfo = model.getFulltextInfo();
            if (fulltextInfo.indexNames.size() > 1
                    && !dialect.supportsMultipleFulltextIndexes()) {
                String msg = String.format(
                        "SQL database supports only one fulltext index, but %d are configured: %s",
                        fulltextInfo.indexNames.size(), fulltextInfo.indexNames);
                throw new StorageException(msg);
            }
            for (String indexName : fulltextInfo.indexNames) {
                String suffix = model.getFulltextIndexSuffix(indexName);
                int ftic = dialect.getFulltextIndexedColumns();
                if (ftic == 1) {
                    table.addIndex(indexName, IndexType.FULLTEXT,
                            model.FULLTEXT_FULLTEXT_KEY + suffix);
                } else if (ftic == 2) {
                    table.addIndex(indexName, IndexType.FULLTEXT,
                            model.FULLTEXT_SIMPLETEXT_KEY + suffix,
                            model.FULLTEXT_BINARYTEXT_KEY + suffix);
                }
            }
        }

        /*
         * binary columns for GC
         */
        for (Entry<String, List<String>> e : model.getBinaryPropertyInfos().entrySet()) {
            String tableName = e.getKey();
            Table table = database.getTable(tableName);
            for (String key : e.getValue()) {
                Select select = new Select(table);
                Column col = table.getColumn(key); // key = name for now
                select.setWhat("DISTINCT " + col.getQuotedName());
                select.setFrom(table.getQuotedName());
                getBinariesSql.add(select.getStatement());
                // in the result column we want the digest, not the binary
                Column resCol = new Column(table, null, ColumnType.STRING, null);
                getBinariesColumns.add(resCol);
            }
        }
    }

    protected void initClusterSQL() {
        TableMaker maker = new TableMaker(model.CLUSTER_NODES_TABLE_NAME);
        maker.newColumn(model.CLUSTER_NODES_NODEID_KEY, ColumnType.CLUSTERNODE);
        maker.newColumn(model.CLUSTER_NODES_CREATED_KEY, ColumnType.TIMESTAMP);

        maker = new TableMaker(model.CLUSTER_INVALS_TABLE_NAME);
        maker.newColumn(model.CLUSTER_INVALS_NODEID_KEY, ColumnType.CLUSTERNODE);
        maker.newColumn(model.CLUSTER_INVALS_ID_KEY, ColumnType.NODEVAL);
        maker.newColumn(model.CLUSTER_INVALS_FRAGMENTS_KEY,
                ColumnType.CLUSTERFRAGS);
        maker.newColumn(model.CLUSTER_INVALS_KIND_KEY, ColumnType.TINYINT);
        maker.table.addIndex(model.CLUSTER_INVALS_NODEID_KEY);
        maker.postProcessClusterInvalidations();
    }

    /**
     * Creates the SQL for the table holding global repository information. This
     * includes the id of the hierarchy root node.
     */
    protected void initRepositorySQL() {
        TableMaker maker = new TableMaker(model.REPOINFO_TABLE_NAME);
        maker.newColumn(model.MAIN_KEY, ColumnType.NODEIDFK);
        maker.newColumn(model.REPOINFO_REPONAME_KEY, ColumnType.SYSNAME);
        maker.postProcessRepository();
    }

    /**
     * Creates the SQL for the table holding hierarchy information.
     */
    protected void initHierarchySQL() {
        TableMaker maker = new TableMaker(model.HIER_TABLE_NAME);
        // if (separateMainTable)
        // maker.newColumn(model.MAIN_KEY, ColumnType.NODEIDFK);
        maker.newColumn(model.MAIN_KEY, ColumnType.NODEID);
        Column column = maker.newColumn(model.HIER_PARENT_KEY,
                ColumnType.NODEIDFKNULL);
        maker.newColumn(model.HIER_CHILD_POS_KEY, ColumnType.INTEGER);
        maker.newColumn(model.HIER_CHILD_NAME_KEY, ColumnType.STRING);
        maker.newColumn(model.HIER_CHILD_ISPROPERTY_KEY, ColumnType.BOOLEAN); // notnull
        // if (!separateMainTable)
        maker.newFragmentFields();
        maker.postProcess();
        maker.postProcessHierarchy();
        // if (!separateMainTable)
        // maker.postProcessIdGeneration();

        maker.table.addIndex(model.HIER_PARENT_KEY);
        maker.table.addIndex(model.HIER_PARENT_KEY, model.HIER_CHILD_NAME_KEY);
        // don't index parent+name+isprop, a simple isprop scan will suffice
        maker.table.addIndex(model.MAIN_PRIMARY_TYPE_KEY);
    }

    protected void initSelectDescendantsSQL() {
        Table hierTable = database.getTable(model.HIER_TABLE_NAME);
        Table proxyTable = database.getTable(model.PROXY_TABLE_NAME);
        Column mainColumn = hierTable.getColumn(model.MAIN_KEY);
        List<Column> whatCols = Arrays.asList(mainColumn,
                hierTable.getColumn(model.HIER_PARENT_KEY),
                hierTable.getColumn(model.MAIN_PRIMARY_TYPE_KEY),
                hierTable.getColumn(model.HIER_CHILD_ISPROPERTY_KEY),
                proxyTable.getColumn(model.PROXY_VERSIONABLE_KEY),
                proxyTable.getColumn(model.PROXY_TARGET_KEY));
        // no mixins, not used to decide if we have a version or proxy
        List<String> whats = new ArrayList<String>(6);
        for (Column col : whatCols) {
            whats.add(col.getFullQuotedName());
        }
        Select select = new Select(null);
        select.setWhat(StringUtils.join(whats, ", "));
        String from = hierTable.getQuotedName() + " LEFT JOIN "
                + proxyTable.getQuotedName() + " ON "
                + mainColumn.getFullQuotedName() + " = "
                + proxyTable.getColumn(model.MAIN_KEY).getFullQuotedName();
        select.setFrom(from);
        String where = dialect.getInTreeSql(mainColumn.getFullQuotedName());
        select.setWhere(where);
        selectDescendantsInfoSql = select.getStatement();
        selectDescendantsInfoWhatColumns = whatCols;
    }

    /**
     * Creates the SQL for the table holding ancestors information.
     * <p>
     * This table holds trigger-updated information extracted from the recursive
     * parent-child relationship in the hierarchy table.
     */
    protected void initAncestorsSQL() {
        TableMaker maker = new TableMaker(model.ANCESTORS_TABLE_NAME);
        maker.newColumn(model.MAIN_KEY, ColumnType.NODEIDFKMUL);
        maker.newColumn(model.ANCESTORS_ANCESTOR_KEY, ColumnType.NODEARRAY);
    }

    /**
     * Creates the SQL for one fragment (simple or collection).
     */
    protected void initFragmentSQL(String tableName) {
        TableMaker maker = new TableMaker(tableName);
        ColumnType type;
        if (tableName.equals(model.HIER_TABLE_NAME)) {
            type = ColumnType.NODEID;
        } else if (tableName.equals(model.LOCK_TABLE_NAME)) {
            type = ColumnType.NODEIDPK; // no foreign key to hierarchy
        } else if (model.isCollectionFragment(tableName)) {
            type = ColumnType.NODEIDFKMUL;
        } else {
            type = ColumnType.NODEIDFK;
        }
        maker.newColumn(model.MAIN_KEY, type);
        maker.newFragmentFields();
        maker.postProcess();
        // if (isMain)
        // maker.postProcessIdGeneration();
    }

    protected void initSelections() {
        for (SelectionType selType : SelectionType.values()) {
            selections.put(selType, new SQLInfoSelection(selType));
        }
    }

    // ----- prepare one table -----

    protected class TableMaker {

        private final String tableName;

        private final Table table;

        private final String orderBy;

        protected TableMaker(String tableName) {
            this.tableName = tableName;
            table = database.addTable(tableName);
            orderBy = model.getCollectionOrderBy(tableName);
        }

        protected void newFragmentFields() {
            Map<String, ColumnType> keysType = model.getFragmentKeysType(tableName);
            for (Entry<String, ColumnType> entry : keysType.entrySet()) {
                newColumn(entry.getKey(), entry.getValue());
            }
        }

        protected Column newColumn(String key, ColumnType type) {
            String columnName = key;
            Column column = table.addColumn(columnName, type, key, model);
            if (type == ColumnType.NODEID) {
                // column.setIdentity(true); if idGenPolicy identity
                column.setNullable(false);
                column.setPrimary(true);
            }
            if (type == ColumnType.NODEIDFK || type == ColumnType.NODEIDPK) {
                column.setNullable(false);
                column.setPrimary(true);
            }
            if (type == ColumnType.NODEIDFKMUL) {
                column.setNullable(false);
                table.addIndex(key);
            }
            if (type == ColumnType.NODEIDFK || type == ColumnType.NODEIDFKNP
                    || type == ColumnType.NODEIDFKNULL
                    || type == ColumnType.NODEIDFKMUL) {
                column.setReferences(database.getTable(model.HIER_TABLE_NAME),
                        model.MAIN_KEY);
            }
            return column;
        }

        // ----------------------- post processing -----------------------

        protected void postProcessClusterInvalidations() {
            clusterInvalidationsColumns = Arrays.asList(
                    table.getColumn(model.CLUSTER_INVALS_ID_KEY),
                    table.getColumn(model.CLUSTER_INVALS_FRAGMENTS_KEY),
                    table.getColumn(model.CLUSTER_INVALS_KIND_KEY));
        }

        protected void postProcessRepository() {
            postProcessRootIdSelect();
            postProcessInsert();
        }

        protected void postProcessRootIdSelect() {
            String what = null;
            String where = null;
            for (Column column : table.getColumns()) {
                String key = column.getKey();
                String qname = column.getQuotedName();
                if (key.equals(model.MAIN_KEY)) {
                    what = qname;
                    selectRootIdWhatColumn = column;
                } else if (key.equals(model.REPOINFO_REPONAME_KEY)) {
                    where = qname + " = ?";
                } else {
                    throw new RuntimeException(column.toString());
                }
            }
            Select select = new Select(table);
            select.setWhat(what);
            select.setFrom(table.getQuotedName());
            select.setWhere(where);
            selectRootIdSql = select.getStatement();
        }

        /**
         * Precompute what we can from the information available for a regular
         * schema table, or a collection table.
         */
        protected void postProcess() {
            postProcessSelectById();
            postProcessInsert();
            postProcessDelete();
            postProcessCopy();
        }

        /**
         * Additional SQL for the hierarchy table.
         */
        protected void postProcessHierarchy() {
            postProcessSelectChildrenIdsAndTypes();
        }

        protected void postProcessSelectById() {
            String[] orderBys = orderBy == null ? NO_ORDER_BY : new String[] {
                    orderBy, ORDER_ASC };
            SQLInfoSelect select = makeSelect(table, orderBys, model.MAIN_KEY);
            selectFragmentById.put(tableName, select);
        }

        protected void postProcessSelectChildrenIdsAndTypes() {
            List<Column> whatColumns = new ArrayList<Column>(2);
            List<String> whats = new ArrayList<String>(2);
            Column column = table.getColumn(model.MAIN_KEY);
            whatColumns.add(column);
            whats.add(column.getQuotedName());
            column = table.getColumn(model.MAIN_PRIMARY_TYPE_KEY);
            whatColumns.add(column);
            whats.add(column.getQuotedName());
            column = table.getColumn(model.MAIN_MIXIN_TYPES_KEY);
            whatColumns.add(column);
            whats.add(column.getQuotedName());
            Select select = new Select(table);
            select.setWhat(StringUtils.join(whats, ", "));
            select.setFrom(table.getQuotedName());
            String where = table.getColumn(model.HIER_PARENT_KEY).getQuotedName()
                    + " = ?";
            select.setWhere(where);
            selectChildrenIdsAndTypesSql = select.getStatement();
            selectChildrenIdsAndTypesWhatColumns = whatColumns;
            // now only complex properties
            where += " AND "
                    + table.getColumn(model.HIER_CHILD_ISPROPERTY_KEY).getQuotedName()
                    + " = " + dialect.toBooleanValueString(true);
            select.setWhere(where);
            selectComplexChildrenIdsAndTypesSql = select.getStatement();
        }

        // TODO optimize multiple inserts into one statement for collections
        protected void postProcessInsert() {
            // insert (implicitly auto-generated sequences not included)
            Collection<Column> columns = table.getColumns();
            List<Column> insertColumns = new ArrayList<Column>(columns.size());
            Insert insert = new Insert(table);
            for (Column column : columns) {
                if (column.isIdentity()) {
                    // identity column is never inserted
                    continue;
                }
                insertColumns.add(column);
                insert.addColumn(column);
            }
            insertSqlMap.put(tableName, insert.getStatement());
            insertColumnsMap.put(tableName, insertColumns);
        }

        protected void postProcessDelete() {
            Delete delete = new Delete(table);
            List<String> wheres = new LinkedList<String>();
            for (Column column : table.getColumns()) {
                if (column.getKey().equals(model.MAIN_KEY)) {
                    wheres.add(column.getQuotedName() + " = ?");
                }
            }
            delete.setWhere(StringUtils.join(wheres, " AND "));
            deleteSqlMap.put(tableName, delete.getStatement());
        }

        // copy of a fragment
        // INSERT INTO foo (id, x, y) SELECT ?, x, y FROM foo WHERE id = ?
        protected void postProcessCopy() {
            Collection<Column> columns = table.getColumns();
            List<String> selectWhats = new ArrayList<String>(columns.size());
            Column copyIdColumn = table.getColumn(model.MAIN_KEY);
            Insert insert = new Insert(table);
            for (Column column : columns) {
                if (column.isIdentity()) {
                    // identity column is never copied
                    continue;
                }
                insert.addColumn(column);
                if (column == copyIdColumn) {
                    // explicit value
                    selectWhats.add("?");
                } else {
                    // otherwise copy value
                    selectWhats.add(column.getQuotedName());
                }
            }
            Select select = new Select(table);
            select.setWhat(StringUtils.join(selectWhats, ", "));
            select.setFrom(table.getQuotedName());
            select.setWhere(copyIdColumn.getQuotedName() + " = ?");
            insert.setValues(select.getStatement());
            copySqlMap.put(tableName, insert.getStatement());
            copyIdColumnMap.put(tableName, copyIdColumn);
        }

    }

    public static class SQLInfoSelect {

        public final String sql;

        public final List<Column> whatColumns;

        public final MapMaker mapMaker;

        public final List<Column> whereColumns;

        public final List<Column> opaqueColumns;

        /**
         * Standard select for given columns.
         */
        public SQLInfoSelect(String sql, List<Column> whatColumns,
                List<Column> whereColumns, List<Column> opaqueColumns) {
            this(sql, whatColumns, null, whereColumns, opaqueColumns);
        }

        /**
         * Select where some column keys may be aliased, and some columns may be
         * computed. The {@link MapMaker} is used by the queryAndFetch() method.
         */
        public SQLInfoSelect(String sql, MapMaker mapMaker) {
            this(sql, null, mapMaker, null, null);
        }

        public SQLInfoSelect(String sql, List<Column> whatColumns,
                MapMaker mapMaker, List<Column> whereColumns,
                List<Column> opaqueColumns) {
            this.sql = sql;
            this.whatColumns = whatColumns;
            this.mapMaker = mapMaker;
            this.whereColumns = whereColumns == null ? null
                    : new ArrayList<Column>(whereColumns);
            this.opaqueColumns = opaqueColumns == null ? null
                    : new ArrayList<Column>(opaqueColumns);
        }
    }

    /**
     * Info about how to do the query to get a {@link Selection}.
     */
    public class SQLInfoSelection {

        public final SelectionType type;

        public final SQLInfoSelect selectAll;

        public final SQLInfoSelect selectFiltered;

        public SQLInfoSelection(SelectionType selType) {
            this.type = selType;
            Table table = database.getTable(selType.tableName);
            SQLInfoSelect selectAll;
            SQLInfoSelect selectFiltered;
            if (selType.criterionKey == null) {
                selectAll = makeSelect(table, NO_ORDER_BY, selType.selKey);
                selectFiltered = makeSelect(table, NO_ORDER_BY, selType.selKey,
                        selType.filterKey);
            } else {
                selectAll = makeSelect(table, NO_ORDER_BY, selType.selKey,
                        selType.criterionKey);
                selectFiltered = makeSelect(table, NO_ORDER_BY, selType.selKey,
                        selType.filterKey, selType.criterionKey);
            }
            this.selectAll = selectAll;
            this.selectFiltered = selectFiltered;
        }
    }

    /**
     * Knows how to build a result map for a row given a {@link ResultSet}. This
     * abstraction may be used to compute some values on the fly.
     */
    public interface MapMaker {
        Map<String, Serializable> makeMap(ResultSet rs) throws SQLException;
    }

    /**
     * Builds the map from a result set given a list of columns and column keys.
     */
    public static class ColumnMapMaker implements MapMaker {
        public final List<Column> columns;

        public final List<String> keys;

        public ColumnMapMaker(List<Column> columns) {
            this.columns = columns;
            keys = new ArrayList<String>(columns.size());
            for (Column column : columns) {
                keys.add(column.getKey());
            }
        }

        public ColumnMapMaker(List<Column> columns, List<String> keys) {
            this.columns = columns;
            this.keys = keys;
        }

        @Override
        public Map<String, Serializable> makeMap(ResultSet rs)
                throws SQLException {
            Map<String, Serializable> map = new HashMap<String, Serializable>();
            int i = 1;
            for (Column column : columns) {
                String key = keys.get(i - 1);
                Serializable value = column.getFromResultSet(rs, i++);
                map.put(key, value);
            }
            return map;
        }
    }

    private static String[] NO_ORDER_BY = new String[0];

    /**
     * Basic SELECT x, y, z FROM table WHERE a = ? AND b = ?
     * <p>
     * with optional ORDER BY x, y DESC
     */
    public static SQLInfoSelect makeSelect(Table table, String[] orderBys,
            String... freeColumns) {
        List<String> freeColumnsList = Arrays.asList(freeColumns);
        List<Column> whatColumns = new LinkedList<Column>();
        List<Column> whereColumns = new LinkedList<Column>();
        List<Column> opaqueColumns = new LinkedList<Column>();
        List<String> whats = new LinkedList<String>();
        List<String> wheres = new LinkedList<String>();
        for (Column column : table.getColumns()) {
            String qname = column.getQuotedName();
            if (freeColumnsList.contains(column.getKey())) {
                whereColumns.add(column);
                wheres.add(qname + " = ?");
            } else if (column.isOpaque()) {
                opaqueColumns.add(column);
            } else {
                whatColumns.add(column);
                whats.add(qname);
            }
        }
        if (whats.isEmpty()) {
            // only opaque columns, don't generate an illegal SELECT
            whats.add(table.getColumn(Model.MAIN_KEY).getQuotedName());
        }
        Select select = new Select(table);
        select.setWhat(StringUtils.join(whats, ", "));
        select.setFrom(table.getQuotedName());
        select.setWhere(StringUtils.join(wheres, " AND "));
        List<String> orders = new LinkedList<String>();
        for (int i = 0; i < orderBys.length; i++) {
            String name = orderBys[i++];
            String ascdesc = orderBys[i].equals(ORDER_DESC) ? " " + ORDER_DESC
                    : "";
            orders.add(table.getColumn(name).getQuotedName() + ascdesc);
        }
        select.setOrderBy(StringUtils.join(orders, ", "));
        return new SQLInfoSelect(select.getStatement(), whatColumns,
                whereColumns, opaqueColumns.isEmpty() ? null : opaqueColumns);
    }

    public void initSQLStatements(Map<String, Serializable> testProps)
            throws IOException {
        sqlStatements = new HashMap<String, List<SQLStatement>>();
        SQLStatement.read(dialect.getSQLStatementsFilename(), sqlStatements);
        if (!testProps.isEmpty()) {
            SQLStatement.read(dialect.getTestSQLStatementsFilename(),
                    sqlStatements);
        }
        sqlStatementsProperties = dialect.getSQLStatementsProperties(model,
                database);
        if (!testProps.isEmpty()) {
            sqlStatementsProperties.putAll(testProps);
        }
    }

    /**
     * Executes the SQL statements for the given category.
     */
    public void executeSQLStatements(String category, JDBCConnection jdbc)
            throws SQLException {
        List<SQLStatement> statements = sqlStatements.get(category);
        if (statements != null) {
            SQLStatement.execute(statements, sqlStatementsProperties, jdbc);
        }
    }

    public int getMaximumArgsForIn() {
        return dialect.getMaximumArgsForIn();
    }

}
