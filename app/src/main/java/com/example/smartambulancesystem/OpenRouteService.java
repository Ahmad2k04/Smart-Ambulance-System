package com.example.smartambulancesystem;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenRouteService {

    @GET("maps/api/directions/json")
    Call<JsonObject> getRoute(

            @Query("origin") String origin,

            @Query("destination") String destination,

            @Query("mode") String mode,

            @Query("departure_time") String departureTime,

            @Query("traffic_model") String trafficModel,

            @Query("key") String apiKey
    );
}