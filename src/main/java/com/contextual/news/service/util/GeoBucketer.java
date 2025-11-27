package com.contextual.news.service.util;

import java.util.ArrayList;
import java.util.List;

public class GeoBucketer {

    private static final double KM_PER_DEGREE = 111.0;

    private final double bucketSizeDegrees;

    public GeoBucketer(double bucketSizeDegrees) {
        if (bucketSizeDegrees <= 0.0) {
            throw new IllegalArgumentException("bucketSizeDegrees must be positive");
        }
        this.bucketSizeDegrees = bucketSizeDegrees;
    }

    public String bucketId(double latitude, double longitude) {
        long latIndex = Math.round(Math.floor(latitude / bucketSizeDegrees));
        long lonIndex = Math.round(Math.floor(longitude / bucketSizeDegrees));
        return latIndex + "_" + lonIndex;
    }

    public List<String> nearbyBuckets(double latitude, double longitude, double radiusKm) {
        int steps = Math.max(1, (int) Math.ceil(radiusKm / (bucketSizeDegrees * KM_PER_DEGREE)));
        long baseLatIndex = Math.round(Math.floor(latitude / bucketSizeDegrees));
        long baseLonIndex = Math.round(Math.floor(longitude / bucketSizeDegrees));
        List<String> buckets = new ArrayList<>();
        for (long lat = baseLatIndex - steps; lat <= baseLatIndex + steps; lat++) {
            for (long lon = baseLonIndex - steps; lon <= baseLonIndex + steps; lon++) {
                buckets.add(lat + "_" + lon);
            }
        }
        return buckets;
    }

    public double bucketCenterLat(String bucketId) {
        long latIndex = Long.parseLong(bucketId.split("_")[0]);
        return (latIndex + 0.5) * bucketSizeDegrees;
    }

    public double bucketCenterLon(String bucketId) {
        long lonIndex = Long.parseLong(bucketId.split("_")[1]);
        return (lonIndex + 0.5) * bucketSizeDegrees;
    }
}

