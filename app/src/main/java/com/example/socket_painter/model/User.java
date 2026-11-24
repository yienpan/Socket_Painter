package com.example.socket_painter.model;

import org.json.JSONException;
import org.json.JSONObject;

public class User {
    private String userId;
    private String userName;
    private String color;
    private String ip;

    public User(String userId, String userName, String color, String ip) {
        this.userId = userId;
        this.userName = userName;
        this.color = color;
        this.ip = ip != null ? ip : "";
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getColor() {
        return color;
    }

    public String getIp() {
        return ip;
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("userId", userId);
        json.put("userName", userName);
        json.put("color", color);
        json.put("ip", ip);
        return json;
    }

    public static User fromJSONObject(JSONObject json) throws JSONException {
        String userId = json.getString("userId");
        String userName = json.getString("userName");
        String color = json.getString("color");
        String ip = json.optString("ip", "");
        return new User(userId, userName, color, ip);
    }
}
