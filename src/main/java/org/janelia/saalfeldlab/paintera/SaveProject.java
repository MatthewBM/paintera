package org.janelia.saalfeldlab.paintera;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.janelia.saalfeldlab.paintera.serialization.Properties;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

public class SaveProject
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static void persistProperties( final String root, final Properties properties, final GsonBuilder builder ) throws IOException
	{
		builder.create().getAdapter( SourceInfo.class );
		LOG.debug( "Persisting properties {} into {}", properties, root );
		N5Helpers.n5Writer( root, builder, 64, 64, 64 ).setAttribute( "", Paintera.PAINTERA_KEY, properties );
	}

}
