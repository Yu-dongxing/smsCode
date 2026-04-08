package com.wzz.smscode.dto.number;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetNumberResponseDTO implements Serializable {
    private Integer status;
    private String msg;
    private String data;
    private String projectName;

    public static GetNumberResponseDTO success(String msg, String data, String projectName) {
        return new GetNumberResponseDTO(0, msg, data, projectName);
    }

    public static GetNumberResponseDTO error(Integer status, String msg) {
        return new GetNumberResponseDTO(status, msg, null, null);
    }
}
