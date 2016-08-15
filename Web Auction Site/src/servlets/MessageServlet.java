package servlets;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dao.MessageDAO;
import dao.MessageDAOI;
import dao.UserDAO;
import dao.UserDAOI;
import entities.Message;
import entities.User;

/**
 * Servlet implementation class MessageServlet
 */
@WebServlet("/Messages")
public class MessageServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
	private static final int ENTRIES_PER_MESSAGE_PAGE = 10;
    /**
     * @see HttpServlet#HttpServlet()
     */
    public MessageServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// First check if our user is logged in.
		RequestDispatcher disp;
		if(request.getSession().getAttribute("userID") == null){
			disp = getServletContext().getRequestDispatcher("/loginerror.jsp");
			disp.forward(request, response);
			return;
		}
		
		if(request.getParameter("sendto") != null){
			request.setAttribute("send-username", request.getParameter("sendto"));
		}
		else{
			request.setAttribute("send-username", "");
		}
		disp = request.getRequestDispatcher("/message_list.jsp");
		disp.forward(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// First check if our user is logged in.
		RequestDispatcher disp;
		if(request.getSession().getAttribute("userID") == null){
			disp = getServletContext().getRequestDispatcher("/loginerror.jsp");
			disp.forward(request, response);
			return;
		}
		
		String action = request.getParameter("action");
		
		if(action == null){
			System.out.println("Null action");
			return ;
		}
		
		if(action.equals("send")){
			// AJAX call 
			// Gather data
			JsonObject data = new Gson().fromJson(request.getReader(), JsonObject.class);
			String user, subject, text;
			user = data.get("msg-to").getAsString();
			subject = data.get("msg-subject").getAsString();
			text = data.get("msg-body").getAsString();
			
			// Check if everything is filled out
			String message = "";
			boolean success = false;
			if(user == null || user.equals("")){
				message = "You must specify the recipient username";
			}
			else if(subject == null || subject.equals("")){
				message = "You must give a subject";
			}
			else if(text == null || text.equals("")){
				message = "Message cannot be empty";
			}
			else{
				// Check if user exists.
				User recipient;
				UserDAOI udao = new UserDAO();
				recipient = udao.findByID(user);
				if(recipient == null){
					message = "User not found";
				}
				else{
					// Get our user.
					User sender = udao.findByID(request.getSession().getAttribute("userID").toString());
					
					// Create message.
					Message msg = new Message();
					msg.setIs_read(false);
					msg.setUser_to(recipient);
					msg.setSubject(subject);
					msg.setText(text);
					msg.setUser_from(sender);
					msg.setUser_to(recipient);
					msg.setTime(new Date());
					System.out.println("Message: " + text);
					MessageDAOI msgdao = new MessageDAO();
					msgdao.create(msg);
					success = true;
					message = "Message sent";
					
				}
			}
			JsonObject reply = new JsonObject();
			reply.addProperty("success",success);
			reply.addProperty("message", message);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(reply.toString());
		}
		else if(action.equals("fetch")){
			JsonObject data = new Gson().fromJson(request.getReader(), JsonObject.class);
			String type = (String)data.get("type").getAsString();
			String page = (String)data.get("page").getAsString();
			
			// Check if we have everything.
			String message = "";
			boolean success = false;
			if(type == null || !(type.equals("sent") || type.equals("received"))){
				message = "Invalid";
			}
			else if(page == null || page.equals("")){
				message = "Page not specified";
			}
			else{
				// Fetch user's messages according to type and page
				int entries_per_page = ENTRIES_PER_MESSAGE_PAGE;
				String userid = request.getSession().getAttribute("userID").toString();
				UserDAOI udao = new UserDAO();
				User myuser = udao.findByID(userid); // Get logged in user.
				MessageDAOI msgdao = new MessageDAO();
				
				// Gather all messages.
				List<Message> msg_list = null;
				if(type.equals("sent")){
					msg_list = msgdao.getSentOf(myuser, Integer.parseInt(page), entries_per_page);
				}
				else{
					msg_list = msgdao.getReceivedOf(myuser, Integer.parseInt(page), entries_per_page);
				}
				
				// Prepare Json Response.
				SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd-MM-yyyy");
				JsonArray msg_arr = new JsonArray();
				JsonObject msg;
				
				for(Message m : msg_list){
					msg = new JsonObject();
					if(type.equals("sent")){
						msg.addProperty("user", m.getUser_to().getUserId());
					}
					else{
						msg.addProperty("user", m.getUser_from().getUserId());
					}
					msg.addProperty("subject", m.getSubject());
					msg.addProperty("date", sdf.format(m.getTime()));
					msg.addProperty("read", m.getIs_read());
					msg.addProperty("body", m.getText());
					msg_arr.add(msg);
				}
				
				int pages_number; // Let the front end know how many pages exists. 
				if(type.equals("sent")){
					pages_number = (int) (Math.ceil(msgdao.getCountSent(myuser)/ (double) ENTRIES_PER_MESSAGE_PAGE));
				}
				else{
					pages_number = (int) (Math.ceil(msgdao.getCountReceived(myuser)/ (double) ENTRIES_PER_MESSAGE_PAGE));
				}
				
				JsonObject reply = new JsonObject();
				reply.add("messages", msg_arr);
				reply.addProperty("pages", pages_number);
				response.setContentType("application/json");
				response.setCharacterEncoding("UTF-8");
				response.getWriter().write(reply.toString());
			}
		}
	}
}