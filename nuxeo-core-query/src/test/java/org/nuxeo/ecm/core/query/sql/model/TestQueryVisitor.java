/*
 * (C) Copyright 2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Florent Guillaume
 */

package org.nuxeo.ecm.core.query.sql.model;

import java.util.Iterator;

import org.nuxeo.ecm.core.query.sql.SQLQueryParser;

import junit.framework.TestCase;

/**
 * Simple test of the visitor using a dumb printer.
 *
 * @author Florent Guillaume
 */
public class TestQueryVisitor extends TestCase {

    private void check(String sql, String expected) {
        PrintVisitor v = new PrintVisitor();
        v.visitQuery(SQLQueryParser.parse(sql));
        assertEquals(expected, v.toString());
    }

    public void testVisitor() throws Exception {
        String sql;
        String expected;

        sql = "select p as p from t where title=\"%test\"";
        expected = "SELECT p FROM t WHERE (title = '%test')";
        check(sql, expected);

        sql = "select p from t where foo in (1, 2)";
        expected = "SELECT p FROM t WHERE (foo IN (1, 2))";
        check(sql, expected);

        sql = "SELECT p, q AS qq, f(x) FROM t, u, v" + //
                " WHERE title = 'ab' AND des = 'cd'" + //
                // " GROUP BY x, y" + // unimpl
                // " HAVING 1+1=2" + // unimpl
                " ORDER BY x DESC,y,z  DESC" + //
                " LIMIT 8   OFFSET 43";
        expected = "SELECT p, q AS qq, f(x) FROM t, u, v" + //
                " WHERE ((title = 'ab') AND (des = 'cd'))" + //
                // " GROUP BY x, y" + //
                // " HAVING 1+1=2" + //
                " ORDER BY x DESC, y, z DESC" + //
                " LIMIT 8 OFFSET 43";
        check(sql, expected);

        sql = "select foo from docs";
        expected = "SELECT foo FROM docs";
        check(sql, expected);

        sql = "select * from d where foo <> DATE '2008-01-01'";
        expected = "SELECT * FROM d WHERE (foo <> DATE '2008-01-01')";
        check(sql, expected);

        // hack around timezone variations for this test
        sql = "select * from d where foo = TIMESTAMP '2008-08-08 12:34:56'";
        expected = "SELECT * FROM d WHERE (foo = TIMESTAMP '2008-08-08T12:34:56.000+00:00')";
        expected = expected.substring(0, expected.length() - 8); // truncate tz
        PrintVisitor v = new PrintVisitor();
        v.visitQuery(SQLQueryParser.parse(sql));
        String got = v.toString();
        got = got.substring(0, got.length() - 8); // truncate timezone
        assertEquals(expected, got);
    }

}

class PrintVisitor extends DefaultQueryVisitor {

    private static final long serialVersionUID = 1L;

    public final StringBuilder buf = new StringBuilder();

    @Override
    public String toString() {
        return buf.toString();
    }

    @Override
    public void visitQuery(SQLQuery node) {
        super.visitQuery(node);
        if (node.limit != 0) {
            buf.append(" LIMIT ");
            buf.append(node.limit);
            if (node.offset != 0) {
                buf.append(" OFFSET ");
                buf.append(node.offset);
            }
        }
    }

    @Override
    public void visitSelectClause(SelectClause node) {
        buf.append("SELECT ");
        SelectList elements = node.elements;
        if (elements.isEmpty()) {
            buf.append("*");
        } else {
            for (int i = 0; i < elements.size(); i++) {
                if (i != 0) {
                    buf.append(", ");
                }
                Operand op = elements.get(i);
                String alias = elements.getKey(i);
                elements.get(i).accept(this);
                if (!alias.equals(op.toString())) {
                    buf.append(" AS ");
                    buf.append(alias);
                }
            }
        }
    }

    @Override
    public void visitFromClause(FromClause node) {
        buf.append(" FROM ");
        FromList elements = node.elements;
        for (int i = 0; i < elements.size(); i++) {
            if (i != 0) {
                buf.append(", ");
            }
            buf.append(elements.get(i));
        }
    }

    @Override
    public void visitWhereClause(WhereClause node) {
        buf.append(" WHERE ");
        super.visitWhereClause(node);
    }

    @Override
    public void visitGroupByClause(GroupByClause node) {
        String[] elements = node.elements;
        if (elements.length == 0) {
            return;
        }
        buf.append(" GROUP BY ");
        for (int i = 0; i < elements.length; i++) {
            if (i != 0) {
                buf.append(", ");
            }
            buf.append(elements[i]);
        }
    }

    @Override
    public void visitHavingClause(HavingClause node) {
        if (node.predicate != null) {
            buf.append(" HAVING ");
            super.visitHavingClause(node);
        }
    }

    @Override
    public void visitOrderByClause(OrderByClause node) {
        if (node.elements.size() == 0) {
            return;
        }
        buf.append(" ORDER BY ");
        super.visitOrderByClause(node);
    }

    @Override
    public void visitOrderByList(OrderByList node) {
        for (int i = 0; i < node.size(); i++) {
            if (i != 0) {
                buf.append(", ");
            }
            node.get(i).accept(this);
        }
    }

    @Override
    public void visitOrderByExpr(OrderByExpr node) {
        super.visitOrderByExpr(node);
        if (node.isDescending) {
            buf.append(" DESC");
        }
    }

    @Override
    public void visitExpression(Expression node) {
        buf.append("(");
        super.visitExpression(node);
        buf.append(")");
    }

    @Override
    public void visitOperator(Operator node) {
        buf.append(" ");
        buf.append(node.toString());
        buf.append(" ");
    }

    @Override
    public void visitReference(Reference node) {
        buf.append(node.name);
    }

    @Override
    public void visitReferenceList(ReferenceList node) {
        for (int i = 0; i < node.size(); i++) {
            if (i != 0) {
                buf.append(", ");
            }
            node.get(i).accept(this);
        }
    }

    @Override
    public void visitLiteral(Literal node) {
    }

    @Override
    public void visitLiteralList(LiteralList node) {
        buf.append('(');
        for (Iterator<Literal> it = node.iterator(); it.hasNext();) {
            it.next().accept(this);
            if (it.hasNext()) {
                buf.append(", ");
            }
        }
        buf.append(')');
    }

    @Override
    public void visitDateLiteral(DateLiteral node) {
        buf.append(node.toString());
    }

    @Override
    public void visitStringLiteral(StringLiteral node) {
        buf.append(node.toString());
    }

    @Override
    public void visitDoubleLiteral(DoubleLiteral node) {
        buf.append(node.toString());
    }

    @Override
    public void visitIntegerLiteral(IntegerLiteral node) {
        buf.append(node.toString());
    }

    @Override
    public void visitFunction(Function node) {
        buf.append(node.name);
        buf.append("(");
        for (Iterator<Operand> it = node.args.iterator(); it.hasNext();) {
            it.next().accept(this);
            if (it.hasNext()) {
                buf.append(", ");
            }
        }
        buf.append(")");
    }

    @Override
    public void visitOperandList(OperandList node) {
        for (Iterator<Operand> it = node.iterator(); it.hasNext();) {
            it.next().accept(this);
            if (it.hasNext()) {
                buf.append(", ");
            }
        }
    }
}
