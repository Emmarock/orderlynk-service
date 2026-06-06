package com.myorderlynk.app.service.notification;

import lombok.Data;

import java.util.List;

@Data
public class ResendEmailRequest {

    private String from;
    private List<String> to;
    private String subject;
    private String html;
}