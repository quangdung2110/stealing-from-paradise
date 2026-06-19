package com.flashsale.commonlib.config;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.VarbinaryJdbcType;

import java.sql.Types;

/**
 * Custom PostgreSQL dialect that forces {@code BLOB} columns to map to {@code BYTEA}
 * and {@code CLOB} columns to map to {@code TEXT} instead of Hibernate 7 defaults.
 *
 * <p><strong>BYTEA mapping:</strong> Axon Framework's {@code JpaTokenStore} stores
 * serialized tokens as binary data. With the standard Hibernate PostgreSQL dialect,
 * a {@code byte[]} field maps to {@code OID} (Large Objects), which conflicts with
 * a {@code BYTEA} column defined by Flyway migration scripts. This dialect overrides
 * {@link #columnType(int)} so that {@link SqlTypes#BLOB} emits {@code bytea} in DDL,
 * and registers {@link VarbinaryJdbcType#INSTANCE} as the BLOB binding type so that
 * {@code PreparedStatement.setBytes()} is used instead of {@code setBlob()} (which
 * writes OID).
 *
 * <p><strong>TEXT mapping:</strong> Axon's JPA Token Store serializes the
 * {@code timestamp} column as a VARCHAR (ISO-8601 string, e.g.
 * "2026-04-21T15:56:16.806Z"). Hibernate may emit {@code CLOB} for String fields
 * on some configurations; this dialect maps {@code SqlTypes.CLOB} to {@code TEXT}
 * to prevent VARCHAR → TEXT coercion mismatches.
 *
 * <p>Usage: set {@code spring.jpa.properties.hibernate.dialect} to this class name.
 */
public class ByteaPostgreSQLDialect extends PostgreSQLDialect {

    public ByteaPostgreSQLDialect() {
        super();
    }

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);

        // Override SqlTypes.BLOB so byte[] binds as BYTEA instead of OID.
        // The base PostgreSQLDialect registers BlobJdbcType.BLOB_BINDING for Types.BLOB,
        // which calls setBlob() and emits DDL as "oid". Registering VarbinaryJdbcType
        // instead causes Hibernate to call setBytes() (compatible with bytea) and,
        // combined with columnType() below, also emits "bytea" in DDL.
        typeContributions.getTypeConfiguration()
                .getJdbcTypeRegistry()
                .addDescriptor(Types.BLOB, VarbinaryJdbcType.INSTANCE);
    }

    @Override
    public String columnType(int sqlTypeCode) {
        if (sqlTypeCode == SqlTypes.BLOB) {
            return "bytea";
        }
        if (sqlTypeCode == SqlTypes.CLOB) {
            return "text";
        }
        return super.columnType(sqlTypeCode);
    }

    @Override
    public String castType(int sqlTypeCode) {
        if (sqlTypeCode == SqlTypes.BLOB) {
            return "bytea";
        }
        if (sqlTypeCode == SqlTypes.CLOB) {
            return "text";
        }
        return super.castType(sqlTypeCode);
    }
}
