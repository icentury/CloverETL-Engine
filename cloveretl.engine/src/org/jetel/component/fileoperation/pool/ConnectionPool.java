package org.jetel.component.fileoperation.pool;


import org.apache.commons.pool.impl.GenericKeyedObjectPool;

/**
 * An implementation of a generic connection pool, a thread-safe singleton.
 * 
 * A connection is identified with an {@link Authority}.
 * When the pool associated with a given authority is empty,
 * an new connection is created and returned.
 * 
 * Connections are tested to be functional before they are returned
 * in {@link #borrowObject(Authority)}.
 * 
 * {@link GenericKeyedObjectPool} ensures that the pools for individual
 * authorities are destroyed when there are no more active or idle objects
 * for a given key.
 * 
 * @author krivanekm
 */
public class ConnectionPool extends GenericKeyedObjectPool<Authority, PoolableConnection> {

	
	/**
	 * SingletonHolder is loaded on the first execution of
	 * DefaultConnectionManager.getInstance() or the first access to SingletonHolder.INSTANCE,
	 * not before.
	 */
	private static class SingletonHolder {
		public static final DefaultConnectionFactory CONNECTION_FACTORY_INSTANCE = new DefaultConnectionFactory();
		public static final ConnectionPool INSTANCE = new ConnectionPool(CONNECTION_FACTORY_INSTANCE);
	}

	public static ConnectionPool getInstance() {
		return SingletonHolder.INSTANCE;
	}

	// TODO add these to Defaults
	/**
	 * How often is the cleanup performed.
	 * 
	 * @see #evict()
	 */
	public static final long CLEANUP_INTERVAL = 1 * 60 * 1000L; // 1 minute
	
	/**
	 * When a connection is idle for more than <code>MAX_IDLE_TIME</code>,
	 * it will be destroyed upon next eviction run.
	 * 
	 * @see #evict()
	 */
	public static final long MAX_IDLE_TIME = 5 * 60 * 1000L; // 5 minutes
	
    /**
	 * Private constructor. 
	 */
	private ConnectionPool(DefaultConnectionFactory factory) {
		super(factory);
		
		this.setWhenExhaustedAction(WHEN_EXHAUSTED_GROW); // when there are not enough connections, create a new one
		this.setTestOnBorrow(true); // important, return working connection
		this.setTestOnReturn(false); // not necessary, the connection will be tested upon next borrow
		
		this.setTimeBetweenEvictionRunsMillis(CLEANUP_INTERVAL); // perform regular cleanup
		this.setTestWhileIdle(true); // destroy non-working idle connections
		this.setMinEvictableIdleTimeMillis(MAX_IDLE_TIME); // if MAX_IDLE_TIME is exceeded, the connection will be destroyed upon next eviction
	}
	
}
