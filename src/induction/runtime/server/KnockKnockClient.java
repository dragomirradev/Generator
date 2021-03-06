package induction.runtime.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 *
 * @author sinantie
 */
public class KnockKnockClient
{

    public static void main(String[] args) throws IOException
    {        
        String host = "localhost";
        int port = 4444;
        if(args.length > 1)
        {
            host = args[0];
            port = Integer.valueOf(args[1]);
        }
        Socket kkSocket = null;
        PrintWriter out = null;
        BufferedReader in = null;

        try {
            kkSocket = new Socket(host, port);
            out = new PrintWriter(kkSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(kkSocket.getInputStream()));
        }
        catch (UnknownHostException e) {
            System.err.println("Don't know about host: " + host);
            System.exit(1);
        }
        catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to: " + host);
            System.exit(1);
        }

        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        String fromServer;
        String fromUser;
                            
        fromUser = stdIn.readLine();
        if (fromUser != null) {
            System.out.println("Client: " + fromUser);
            out.println(fromUser);
        }
        fromServer = in.readLine();
        System.out.println("Server: " + fromServer);            

//        while ((fromServer = in.readLine()) != null) {
//            System.out.println("Server: " + fromServer);
//            if (fromServer.equals("Bye.")) {
//                break;
//            }
//
//            fromUser = stdIn.readLine();
//            if (fromUser != null) {
//                System.out.println("Client: " + fromUser);
//                out.println(fromUser);
//            }
//        }

        out.close();
        in.close();
        stdIn.close();
        kkSocket.close();
    }
}
