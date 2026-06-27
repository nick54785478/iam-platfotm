package com.example.demo.iface.dto.res;

import com.example.demo.application.shared.dto.PermissionDictGottenResult;
import com.example.demo.infra.projection.view.PermissionDictView;

import java.util.List;

public class PermissionDictResponse {

    public record PermissionDictGottenResource(String code, String message, List<PermissionDictGottenResult> data){}

}
