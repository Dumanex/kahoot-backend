package com.kahoot.kahoot_backend.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

// Hides from the SQL log only the query GameAutoFinalizeService runs every second
// (findByStatusAndQuestionFinalizedFalse), so other SQL stays visible. Registered in logback-spring.xml.
public class AutoFinalizeSqlLogFilter extends Filter<ILoggingEvent> {
    private static final String SQL_LOGGER = "org.hibernate.SQL";
    private static final String AUTO_FINALIZE_CONDITION = "question_finalized=false";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (SQL_LOGGER.equals(event.getLoggerName()) && event.getFormattedMessage().contains(AUTO_FINALIZE_CONDITION)) {
            return FilterReply.DENY;
        }

        return FilterReply.NEUTRAL;
    }
}
