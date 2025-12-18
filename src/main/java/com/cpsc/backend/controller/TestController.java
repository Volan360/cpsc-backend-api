package com.cpsc.backend.controller;

import com.cpsc.backend.api.TestApi;
import com.cpsc.backend.model.Hello200Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController implements TestApi {

    @Override
    public ResponseEntity<Hello200Response> hello() {
        Hello200Response response = new Hello200Response();
        response.setMessage("Hello from CPSC Backend API!");
        response.setStatus("success");
        return ResponseEntity.ok(response);
    }
}
