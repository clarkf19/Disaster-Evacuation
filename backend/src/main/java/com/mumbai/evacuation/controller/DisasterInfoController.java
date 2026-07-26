package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.DisasterProtectionGuideDTO;
import com.mumbai.evacuation.dto.HospitalDTO;
import com.mumbai.evacuation.service.DisasterInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/disaster-info")
@CrossOrigin(origins = "*")
public class DisasterInfoController {

    @Autowired
    private DisasterInfoService disasterInfoService;

    @GetMapping("/guides")
    public ResponseEntity<Map<String, DisasterProtectionGuideDTO>> getAllGuides() {
        return ResponseEntity.ok(disasterInfoService.getAllGuides());
    }

    @GetMapping("/guides/{type}")
    public ResponseEntity<DisasterProtectionGuideDTO> getGuideByType(@PathVariable String type) {
        return ResponseEntity.ok(disasterInfoService.getGuide(type));
    }

    @GetMapping("/hospitals")
    public ResponseEntity<List<HospitalDTO>> getHospitals(
            @RequestParam(required = false) String disasterType,
            @RequestParam(required = false) String region) {
        List<HospitalDTO> result = disasterInfoService.getHospitalsByFilter(disasterType, region);
        return ResponseEntity.ok(result);
    }
}
