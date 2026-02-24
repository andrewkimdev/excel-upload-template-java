package com.foo.excel.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UploadIndexController {

  @GetMapping("/")
  public String index() {
    return "index";
  }
}
