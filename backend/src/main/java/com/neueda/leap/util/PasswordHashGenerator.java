package com.neueda.leap.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility for generating BCrypt password hashes for development/testing.
 * 
 * Run this main method to generate hashes for test users.
 * Example password: tadpole1
 * 
 * Usage:
 *   java -cp ".:target/classes:target/lib/*" com.neueda.leap.util.PasswordHashGenerator
 */
public class PasswordHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        String testPassword = "tadpole1";
        
        String[] usernames = {
            "admin01", "auditor01", "analyst01",
            "advisor01", "advisor02", "advisor03", "advisor04", "advisor05", 
            "advisor06", "advisor07", "advisor08", "advisor09", "advisor10",
            "client01", "client02", "client03", "client04", "client05",
            "client06", "client07", "client08", "client09", "client10"
        };
        
        System.out.println("-- Generated BCrypt hashes for password: " + testPassword);
        System.out.println("-- Copy and paste the UPDATE statements below into your database\n");
        
        String hash = encoder.encode(testPassword);
        
        for (String username : usernames) {
            System.out.println("UPDATE users SET password_hash = '" + hash + "' WHERE username = '" + username + "';");
        }
        
        System.out.println("\n-- Alternatively, use this single statement to update all test users:");
        System.out.println("UPDATE users SET password_hash = '" + hash + "' WHERE username IN (");
        for (int i = 0; i < usernames.length; i++) {
            if (i < usernames.length - 1) {
                System.out.println("    '" + usernames[i] + "',");
            } else {
                System.out.println("    '" + usernames[i] + "'");
            }
        }
        System.out.println(");");
    }
}
