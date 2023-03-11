package org.example;

import com.albert.net.remote.protocol.JSONSerializer;
import io.netty.buffer.ByteBuf;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Unit test for simple App.
 */
public class AppTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp()
    {
//        assertTrue( true );
//        log4jTester();
//        byteBufferTester();
        jsonTester();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AppTest.class);
    public void log4jTester() {
        LOGGER.info("asdsd{}", 123);
    }

    public void byteBufferTester() {
        byte[] b = "hello world".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(b);
        while(buffer.hasRemaining()) {
            System.out.println(buffer.get());
        }
    }

    public void jsonTester() {
        JsonClassTester t = new JsonClassTester("123", "456");
        System.out.println(JSONSerializer.toJson(t));
    }
}
