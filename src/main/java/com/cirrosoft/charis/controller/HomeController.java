package com.cirrosoft.charis.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HomeController {

    @RequestMapping("/index")
    public String home() {
        return "index";
    }

    @RequestMapping("/sample")
    public String sample() throws Exception {
        if (true) {
            throw new Exception("Hellows");
        }
        return "helpit";
    }

}
