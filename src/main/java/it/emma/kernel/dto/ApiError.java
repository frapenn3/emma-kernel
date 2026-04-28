package it.emma.kernel.dto;

public class ApiError {
  public String code;
  public String error;

  public ApiError() {}

  public ApiError(String code, String error) {
    this.code = code;
    this.error = error;
  }
}
