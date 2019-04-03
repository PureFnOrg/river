package org.purefn.river;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import clojure.lang.ILookup;
import clojure.lang.Keyword;

public class Message extends ConsumerRecord implements ILookup {

    private Object fetchKey(Object key) {
	if (!(key instanceof Keyword)) {
	    return null;
	}	

	Keyword k = (Keyword) key;
	switch (k.getName()) {
	    case "key":
		return super.key();
	    case "value":
		return super.value();
	    case "partition":
		return super.partition();
	    case "topic":
		return super.topic();
	}

	return null;
    }

    public Object valAt(Object key) {
	return fetchKey(key);
    }

    public Object valAt(Object key, Object notFound) {
	Object val = fetchKey(key);
	return val == null ? notFound : val;
    }

    // TODO
    // ConsumerRecord(java.lang.String topic, int partition, long offset, long timestamp, org.apache.kafka.common.record.TimestampType timestampType, java.lang.Long checksum, int serializedKeySize, int serializedValueSize, K key, V value, Headers headers, java.util.Optional<java.lang.Integer> leaderEpoch)
    public Message(ConsumerRecord record) {
	super(record.topic(),
	      record.partition(),
	      record.offset(),
	      record.key(),
	      record.value());
    }
}
