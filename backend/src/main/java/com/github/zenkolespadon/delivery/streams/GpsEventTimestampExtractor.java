package com.github.zenkolespadon.delivery.streams;

import com.github.zenkolespadon.delivery.event.GpsEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

public class GpsEventTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        if (record.value() instanceof GpsEvent event && event.eventTimestamp() != null) {
            return event.eventTimestamp().toEpochMilli();
        }

        return record.timestamp();
    }
}
