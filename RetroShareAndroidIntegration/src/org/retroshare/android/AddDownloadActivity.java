/**
 * @license
 *
 * Copyright (c) 2013 Gioacchino Mazzurco <gio@eigenlab.org>.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.retroshare.android;

import org.retroshare.android.RsCtrlService.RsMessage;

import com.google.protobuf.InvalidProtocolBufferException;

import rsctrl.core.Core;
import rsctrl.core.Core.File;
import rsctrl.core.Core.Status.StatusCode;
import rsctrl.files.Files.RequestControlDownload.Action;
import rsctrl.files.Files.ResponseControlDownload;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class AddDownloadActivity extends ProxiedActivityBase
{
	private static final String TAG="AddDownloadActivity";
	
	TextView textViewName;
	TextView textViewSize;
	TextView textViewHash;
	TextView textViewResult;
	
	Button buttonDownload;
	
	Core.File mFile=null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_download);
        
    	textViewName=(TextView)findViewById(R.id.textViewFileName_DownloadActivity);
    	textViewSize=(TextView)findViewById(R.id.textViewFileSize_DownloadActivity);
    	textViewHash=(TextView)findViewById(R.id.textViewFileHash_DownloadActivity);
    	textViewResult=(TextView)findViewById(R.id.textViewResult_DownloadActivity);
    	
    	buttonDownload=(Button)findViewById(R.id.buttonDownload_DownloadActivity);
    	
    	buttonDownload.setVisibility(View.GONE);
    	
    	if(getIntent().hasExtra("File")){
            try{
            mFile = File.parseFrom(getIntent().getByteArrayExtra("File"));
            }catch (InvalidProtocolBufferException e) { e.printStackTrace(); }
        }else{
	    	Uri uri = getIntent().getData();
	    	mFile=File.newBuilder()
	    			.setName(uri.getQueryParameter("name"))
	    			.setHash(uri.getQueryParameter("hash"))
	    			.setSize(Long.parseLong(uri.getQueryParameter("size")))
	    			.build();
    	}
    }
    
    @Override
    protected void onServiceConnected()
	{
    	if(mFile!=null){
        	textViewName.setText(mFile.getName());
        	textViewSize.setText(Long.toString(mFile.getSize()));
        	textViewHash.setText(mFile.getHash());
    	}
    	if(getConnectedServer().isOnline()){
    		buttonDownload.setVisibility(View.VISIBLE);
    		textViewResult.setVisibility(View.GONE);
    	}else{
    		buttonDownload.setVisibility(View.GONE);
    		textViewResult.setVisibility(View.VISIBLE);
    		textViewResult.setText("you have to be connected to download a file");
    	}
    }
    
    
    public void onButtonDownloadClick(View v)
	{
    	buttonDownload.setVisibility(View.GONE);
    	textViewResult.setVisibility(View.VISIBLE);
    	textViewResult.setText("processing...");
    	if(mFile!=null){
        	getConnectedServer().mRsFilesService.sendRequestControlDownload(mFile, Action.ACTION_START,new RsMessageHandler(){

				@Override
				protected void rsHandleMsg(RsMessage msg) {
					try {
						ResponseControlDownload resp=ResponseControlDownload.parseFrom(msg.body);
						//textViewResult.setText(resp.getStatus().toString());
			    		if(resp.getStatus().getCode().equals(StatusCode.SUCCESS)){
			    			textViewResult.setText("ok");
			    		}else{
			    			textViewResult.setText("nok");
			    		}
					} catch (InvalidProtocolBufferException e) { e.printStackTrace(); }
				}
        		
        	});
    	}
    }
}
