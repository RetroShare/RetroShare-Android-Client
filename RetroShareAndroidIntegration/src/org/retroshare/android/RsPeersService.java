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

import android.os.Looper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rsctrl.core.Core;
import rsctrl.core.Core.Location;
import rsctrl.core.Core.Person;
import rsctrl.peers.Peers;
import rsctrl.peers.Peers.RequestPeers;
import rsctrl.peers.Peers.ResponsePeerList;

import org.retroshare.android.RsCtrlService.RsMessage;

import com.google.protobuf.InvalidProtocolBufferException;

public class RsPeersService implements RsServiceInterface
{
	private static final String TAG() { return "RsPeersService"; }

	RsCtrlService mRsCtrlService;
	HandlerThreadInterface mUiThreadHandler;

    OwnIdReceivedHandler mOwnIdReceivedHandler;
    Thread mOwnIdReceivedHandlerThread;

	RsPeersService(RsCtrlService s, HandlerThreadInterface u)
	{
		mRsCtrlService = s;
		mUiThreadHandler = u;

        // TODO Port/generalize this indipendent from ui message handling system to other Rs*Sevice too
        mOwnIdReceivedHandlerThread = new Thread(new Runnable() { @Override public void run() { Looper.prepare(); mOwnIdReceivedHandler = new OwnIdReceivedHandler(); Looper.loop(); } });
        mOwnIdReceivedHandlerThread.start();
	}

    private class OwnIdReceivedHandler extends RsMessageHandler { @Override protected void rsHandleMsg(RsMessage msg) { try { ownPerson = ResponsePeerList.parseFrom(msg.body).getPeersList().get(0); } catch (InvalidProtocolBufferException e) { e.printStackTrace(); } } }
	public static interface PeersServiceListener { public void update(); }
	
	private Set<PeersServiceListener> mListeners = new HashSet<PeersServiceListener>();
	public void registerListener(PeersServiceListener l) { mListeners.add(l); }
	public void unregisterListener(PeersServiceListener l) { mListeners.remove(l); }
	private void _notifyListeners() { if(mUiThreadHandler != null) { mUiThreadHandler.postToHandlerThread(new Runnable() {
		@Override
		public void run() {
			for (PeersServiceListener l : mListeners) {
				l.update();
			}
			;
		}
	}); }	}

	// TODO check if we can take more advantage of the fact we have peers in a map in PeersService clients
	private Map<String, Person> mPersons = new HashMap<String, Person>(); // <String:PGP_ID, Person>
	public Person getPersonByPgpId(String pgpId) { return mPersons.get(pgpId); }
	public List<Person> getPersonsByRelationship(Collection<Person.Relationship> relationships)
	{
		List<Person> ret = new ArrayList<Person>();
		for (Person p : mPersons.values()) if(relationships.contains(p.getRelation())) ret.add(p);
		return ret;
	}
	public Collection<Person> getPersonsByRelationship(Person.Relationship relationship)
	{
		Collection<Person> ret = new ArrayList<Person>();
		for (Person p : mPersons.values()) if(relationship.equals(p.getRelation())) ret.add(p);
		return ret;
	}
	public Collection<Person> getPersons() { return mPersons.values(); }
	public Person getPersonBySslId(String sslId)
	{
		for( Person p : getPersons() ) for( Location l : p.getLocationsList() ) if ( l.getSslId().equals(sslId) ) return p;
		throw new RuntimeException("There is no Person with a location matching sslId " + sslId);
	}
	private Person ownPerson;
	public Person getOwnPerson()
	{
		if(ownPerson == null)
		{
			for (Person p : mPersons.values())
			{
				if(p.getRelation() == Person.Relationship.YOURSELF)
				{
					ownPerson = p;
					break;
				}
			}
		}
		return ownPerson;
	}

	public void requestPersonsUpdate(RequestPeers.SetOption option, RequestPeers.InfoOption info)
	{
		RequestPeers.Builder reqb = RequestPeers.newBuilder();
		reqb.setSet(option);
		reqb.setInfo(info);
		RequestPeers req = reqb.build();
		byte[] b;
		b = req.toByteArray();
		RsMessage msg = new RsMessage();
		msg.msgId = RsCtrlService.constructMsgId(Core.ExtensionId.CORE_VALUE, Core.PackageId.PEERS_VALUE, Peers.RequestMsgIds.MsgId_RequestPeers_VALUE, false);
		msg.body = b;
		mRsCtrlService.sendMsg(msg);
	}

	public void requestSetFriend(Person p, boolean makeFriend)
	{
		int messageIg = RsCtrlService.constructMsgId(Core.ExtensionId.CORE_VALUE, Core.PackageId.PEERS_VALUE, Peers.RequestMsgIds.MsgId_RequestAddPeer_VALUE, false);

		Peers.RequestAddPeer.Builder messageBody = Peers.RequestAddPeer.newBuilder();
		messageBody.setPgpId(p.getGpgId());
		if(makeFriend) messageBody.setCmd(Peers.RequestAddPeer.AddCmd.ADD);
		else messageBody.setCmd( Peers.RequestAddPeer.AddCmd.REMOVE);

		RsMessage reqMsg = new RsMessage( messageIg, messageBody.build().toByteArray() );

		mRsCtrlService.sendMsg(reqMsg);
	}

	@Override
	public void handleMessage(RsMessage msg)
	{
		if( msg.msgId == ( RsCtrlService.RESPONSE | (Core.PackageId.PEERS_VALUE << 8) | Peers.ResponseMsgIds.MsgId_ResponsePeerList_VALUE ) )
		{
			try
			{
				for (Person p : ResponsePeerList.parseFrom(msg.body).getPeersList()) mPersons.put(p.getGpgId(), p);
				_notifyListeners();
			}
			catch (InvalidProtocolBufferException e) { e.printStackTrace();	}
		}
	}
}
