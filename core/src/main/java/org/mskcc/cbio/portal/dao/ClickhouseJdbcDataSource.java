package org.mskcc.cbio.portal.dao;

import org.apache.commons.dbcp2.BasicDataSource;

public class ClickhouseJdbcDataSource extends BasicDataSource {
    public ClickhouseJdbcDataSource() {
        this.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        this.setUsername("cbio");
        this.setPassword("P@ssword1");
        this.setUrl("jdbc:ch://localhost:8123/cbioportal?user=cbio&password=P@ssword1&zeroDateTimeBehavior=convertToNull&useSSL=false");
        this.setJmxName("org.cbioportal:DataSource=clickhouse");
    }
}