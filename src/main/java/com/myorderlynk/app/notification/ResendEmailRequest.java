package com.myorderlynk.app.notification;

import lombok.Data;

import java.util.List;

@Data
public class ResendEmailRequest {

    private String from;
    private List<String> to;
    private String subject;
    private String html;
}