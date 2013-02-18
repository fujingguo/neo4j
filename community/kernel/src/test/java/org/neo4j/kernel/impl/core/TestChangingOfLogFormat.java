/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.core;

import static org.junit.Assert.fail;
import static org.neo4j.kernel.impl.nioneo.store.TestXa.copyLogicalLog;
import static org.neo4j.kernel.impl.nioneo.store.TestXa.renameCopiedLogicalLog;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.Pair;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.neo4j.test.impl.EphemeralFileSystemAbstraction;

public class TestChangingOfLogFormat
{
    @Test
    public void inabilityToStartFromOldFormatFromNonCleanShutdown() throws Exception
    {
        File storeDir = new File( "target/var/oldlog" );
        GraphDatabaseService db = factory.newImpermanentDatabase( storeDir.getPath() );
        File logBaseFileName = ((GraphDatabaseAPI)db).getXaDataSourceManager().getNeoStoreDataSource().getXaContainer().getLogicalLog().getBaseFileName();
        Transaction tx = db.beginTx();
        db.createNode();
        tx.success();
        tx.finish();
        
        Pair<Pair<File, File>, Pair<File, File>> copy = copyLogicalLog( fileSystem, logBaseFileName );
        decrementLogFormat( copy.other().other() );
        db.shutdown();
        renameCopiedLogicalLog( fileSystem, copy );
        
        try
        {
            db = factory.newImpermanentDatabase( storeDir.getPath() );
            fail( "Shouldn't be able to do recovery (and upgrade log format version) on non-clean shutdown" );
        }
        catch ( Exception e )
        {   // Good
            e.printStackTrace();
        }
    }
    
    private void decrementLogFormat( File file ) throws IOException
    {
        // Gotten from LogIoUtils class
        FileChannel channel = fileSystem.open( file, "rw" );
        ByteBuffer buffer = ByteBuffer.wrap( new byte[8] );
        channel.read( buffer );
        buffer.flip();
        long version = buffer.getLong();
        long logFormatVersion = (version >>> 56);
        version = version & 0x00FFFFFFFFFFFFFFL;
        long oldVersion = version | ( (logFormatVersion-1) << 56 );
        channel.position( 0 );
        buffer.clear();
        buffer.putLong( oldVersion );
        buffer.flip();
        channel.write( buffer );
        channel.close();
    }
    
    private final EphemeralFileSystemAbstraction fileSystem = new EphemeralFileSystemAbstraction();
    private final TestGraphDatabaseFactory factory = new TestGraphDatabaseFactory().setFileSystem( fileSystem );
}
