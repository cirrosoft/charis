package com.cirrosoft.charis.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HomeController {

    @Autowired
    JdbcTemplate template;

    @RequestMapping("/index")
    public String home() {
        return "index";
    }

    @RequestMapping("/data")
    public ResponseEntity<String> data() {
        SqlRowSet rows = template.queryForRowSet("select * from user;");
        String users = "";
        while (rows.next()) {
            users += rows.getString("username") + "\n";
        }
        return ResponseEntity.ok(users);
    }
}
