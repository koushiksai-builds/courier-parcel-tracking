package edu.vitap.users.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserServiceStatusController {

    @GetMapping("/")
    public String status() {
        return "Courier User Service is running";
    }
}