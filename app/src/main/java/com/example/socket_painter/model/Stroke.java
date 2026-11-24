package com.example.socket_painter.model;

import android.graphics.PointF;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Stroke {
    private String strokeId;
    private String userId;
    private String userName;
    private String color; // Hex color string e.g. "#FF0000"
    private List<PointF> points;

    public Stroke(String strokeId, String userId, String userName, String color, List<PointF> points) {
        this.strokeId = strokeId;
        this.userId = userId;
        this.userName = userName;
        this.color = color;
        this.points = points != null ? points : new ArrayList<>();
    }

    public String getStrokeId() {
        return strokeId;
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

    public List<PointF> getPoints() {
        return points;
    }

    public void addPoint(PointF point) {
        points.add(point);
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("strokeId", strokeId);
        json.put("userId", userId);
        json.put("userName", userName);
        json.put("color", color);

        JSONArray jPoints = new JSONArray();
        for (PointF p : points) {
            JSONObject jp = new JSONObject();
            jp.put("x", p.x);
            jp.put("y", p.y);
            jPoints.put(jp);
        }
        json.put("points", jPoints);
        return json;
    }

    public static Stroke fromJSONObject(JSONObject json) throws JSONException {
        String strokeId = json.getString("strokeId");
        String userId = json.getString("userId");
        String userName = json.optString("userName", "");
        String color = json.getString("color");

        List<PointF> points = new ArrayList<>();
        JSONArray jPoints = json.getJSONArray("points");
        for (int i = 0; i < jPoints.length(); i++) {
            JSONObject jp = jPoints.getJSONObject(i);
            float x = (float) jp.getDouble("x");
            float y = (float) jp.getDouble("y");
            points.add(new PointF(x, y));
        }

        return new Stroke(strokeId, userId, userName, color, points);
    }
}
