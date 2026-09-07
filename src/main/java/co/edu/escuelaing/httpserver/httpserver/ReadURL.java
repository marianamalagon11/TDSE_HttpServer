/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package co.edu.escuelaing.httpserver.httpserver;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 *
 * @author maria
 */
public class ReadURL {
    
    public static void main (String[] args)throws URISyntaxException, MalformedURLException{
        URL personalSite = new URI("http://ldbn.escuelaing.edu.co:5678/respuestaslab.txt?year=2026&semestre=2#projects").toURL();
        
        System.out.println("Protocol: " + personalSite.getProtocol());
        System.out.println("getAuthority: " + personalSite.getAuthority());
        System.out.println("getHost: " + personalSite.getHost());
        System.out.println("getPort: " + personalSite.getPort());
        System.out.println("getPath: " + personalSite.getPath());
        System.out.println("getQuery: " + personalSite.getQuery());
        System.out.println("getFile: " + personalSite.getFile());
        
        
    }
}
