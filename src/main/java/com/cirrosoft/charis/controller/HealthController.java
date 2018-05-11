package com.cirrosoft.charis.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;

@Controller
public class HealthController {

    @Autowired
    private Environment env;

    @RequestMapping(value = "/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("");
    }

    @RequestMapping(value = "/buildinfo", produces = "application/json")
    public ResponseEntity<String> info() {
        HashMap<String, String> map = new HashMap<>();
        String[] buildNames = {
                "build.appName",
                "build.color",
                "build.buildNumber",
                "build.instanceName",
                "build.instanceType",
                "build.instanceImage",
                "build.instanceSecurityGroup",
                "build.instanceKeyPair",
                "build.commitHash",
                "build.commitHashFull",
                "build.dockerName",
                "build.dockerTag",
                "build.awsCredential"
        };
        for (String key : buildNames) {
            try {
                String value = env.getProperty(key);
                map.put(key.replace("build.", ""), value);
            } catch (Exception e) {
                ;
            }
        }
        String json = "";
        try {
            json = new ObjectMapper().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return ResponseEntity.ok(json);
    }

}
