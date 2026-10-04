package com.Wakala.v1.Security;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Inaunda unique ID kila app inapo-start.
 * Boot ID inatumika kwenye JWT ili ku-invalidate tokens zote za zamani
 * baada ya restart ya app.
 */
@Component
public class AppBootId {

    private final String id;

    public AppBootId() {
        this.id = UUID.randomUUID().toString();
        System.out.println("══════════════════════════════════════════════");
        System.out.println(" App Boot ID: " + this.id);
        System.out.println("══════════════════════════════════════════════");
    }

    public String getId() {
        return id;
    }
}