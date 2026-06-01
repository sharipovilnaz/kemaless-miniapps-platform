package com.kemaless.core.audit.repository;

public interface AuditMiniAppMetricsProjection {

    String getRealmCode();

    String getMiniAppCode();

    String getAction();

    String getStatus();

    long getEventsCount();
}
