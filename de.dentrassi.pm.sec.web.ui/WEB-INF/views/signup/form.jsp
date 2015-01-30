<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true"%>

<%@page import="de.dentrassi.pm.sec.UserStorage"%>

<%@ taglib tagdir="/WEB-INF/tags/main" prefix="h" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://dentrassi.de/osgi/web/form" prefix="form" %>

<h:main title="Register">

<form:form action="" method="POST"  cssClass="form-horizontal">

<div class="container-fluid">

<div class="row">

	<div class="col-md-10 col-lg-9">
	   
	    <h:formEntry label="E-Mail"  command="command" path="email">
	        <form:input path="email" cssClass="form-control" type="email"/>
	    </h:formEntry>
	    
	    <h:formEntry label="Password"  command="command" path="password">
	        <form:input path="password" cssClass="form-control" type="password"/>
	    </h:formEntry>
	    
	    <h:formEntry label="Password (repeat)"  command="command" path="passwordRepeat">
	        <form:input path="passwordRepeat" cssClass="form-control" type="password"/>
	    </h:formEntry>
	    
	    <h:formEntry label="Real Name"  command="command" path="name">
	        <form:input path="name" cssClass="form-control" placeholder="Optional real name"/>
	    </h:formEntry>

		<div class="form-group">
			<div class="col-sm-offset-2 col-sm-10">
				<button type="submit" class="btn btn-primary">Register</button>
			</div>
		</div>
	</div>
	
	<div class="col-md-2 col-lg-3">
	   <c:if test="${bindingResult.hasMarker('duplicateEmail') }">
	   
	   <div class="panel panel-info">
	       <div class="panel-heading"><h3 class="panel-title">Re-Request E-Mail</h3></div>
	       <div class="panel-body">
	           If you already signed up, but did not get the verification e-mail, you can
	           <a href="<c:url value="/signup/requestEmail"/>">re-request</a> this e-mail.
	       </div>
	   </div>
	   </c:if>
	</div>
	
</div></div>

</form:form>

</h:main>