package org.purefn.river;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import clojure.lang.ILookup;
import clojure.lang.Keyword;

public class Message<K,V> extends ConsumerRecord<K,V> implements ILookup {

    private Object fetchKey(Object key) {
	if (!(key instanceof Keyword)) {
	    return null;
	}	

	Keyword k = (Keyword) key;
	switch (k.getName()) {
	    case "topic":
		return super.topic();
	    case "partition":
		return super.partition();
	    case "offset":
		return super.offset();
	    case "timestamp":
		return super.timestamp();
	    case "timestamp-type":
		return super.timestampType();
	    case "serialized-key-size":
		return super.serializedKeySize();
	    case "serialized-value-size":
		return super.serializedValueSize();
	    case "key":
		return super.key();
	    case "value":
		return super.value();
	    case "headers":
		return super.headers();
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

    public Message(ConsumerRecord<K,V> record) {
	super(record.topic(),
	      record.partition(),
	      record.offset(),
	      record.timestamp(),
	      record.timestampType(),
	      0L,  // checksum is deprecated as of 0.11
	      record.serializedKeySize(),
	      record.serializedValueSize(),
	      record.key(),
	      record.value(),
	      record.headers());
    }
}
