package com.bidv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RestController
public class TransactionController {
	@Autowired
	TranRepository repo;
	
    @RequestMapping(value = "/api/create", method = RequestMethod.POST, produces = {
			MediaType.APPLICATION_JSON_VALUE })
    public TranResponse create(@RequestBody TranRequest req) {
    	
    	String encData = req.getData();
    	byte[] rawData = PGPEncryptDecryptUtil.decryptTextMessagePGP(encData.getBytes(), "0x3720DBF7-sec.asc", "123456");
    	String jsonTrans = new String(rawData, StandardCharsets.UTF_8);
    	
    	ObjectMapper mapper = new ObjectMapper();
    	try {
			List<Transaction> lstTrans = Arrays.asList(mapper.readValue(jsonTrans, Transaction[].class));
	        repo.saveAll(lstTrans);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
		return new TranResponse("0","Thành công") ;
    }
    
    @GetMapping("/api/getAll")
	public ResponseEntity<List<Transaction>> getAll() {
		try {
			List<Transaction> trans = new ArrayList<Transaction>();
			repo.findAll().forEach(trans::add);


			return new ResponseEntity<>(trans, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
