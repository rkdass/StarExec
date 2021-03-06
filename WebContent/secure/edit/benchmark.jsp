<%@page contentType="text/html" pageEncoding="UTF-8"
        import="org.starexec.constants.DB,org.starexec.constants.R,org.starexec.data.database.Benchmarks, org.starexec.data.database.Permissions, org.starexec.data.database.Processors, org.starexec.data.security.GeneralSecurity, org.starexec.data.to.Benchmark, org.starexec.data.to.enums.ProcessorType, org.starexec.util.SessionUtil" %>
<%@taglib prefix="star" tagdir="/WEB-INF/tags" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%
	try {
		int userId = SessionUtil.getUserId(request);
		int benchId = Integer.parseInt(request.getParameter("id"));
		request.setAttribute("benchNameLen", DB.BENCH_NAME_LEN);
		request.setAttribute("benchDescLen", DB.BENCH_DESC_LEN);
		request.setAttribute("nameRegex", R.PRIMITIVE_NAME_PATTERN);
		Benchmark b = null;
		if (Permissions.canUserSeeBench(benchId, userId)) {
			b = Benchmarks.get(benchId);
		}


		if (b != null) {
			// Ensure the user visiting this page is the owner of the benchmark
			if (userId != b.getUserId() &&
					!GeneralSecurity.hasAdminReadPrivileges(userId)) {
				response.sendError(
						HttpServletResponse.SC_FORBIDDEN,
						"Only the owner of this benchmark can edit details about it."
				);
				return;
			} else {
				request.setAttribute("bench", b);
				if (b.isDownloadable()) {
					request.setAttribute("isDownloadable", "checked");
					request.setAttribute("isNotDownloadable", "");
				} else {
					request.setAttribute("isDownloadable", "");
					request.setAttribute("isNotDownloadable", "checked");
				}

				request.setAttribute(
						"types", Processors.getAll(ProcessorType.BENCH));
			}
		} else {
			if (Benchmarks.isBenchmarkDeleted(benchId)) {
				response.sendError(
						HttpServletResponse.SC_NOT_FOUND,
						"This benchmark has been deleted. You likely want to remove it from your spaces"
				);
				return;
			} else {
				response.sendError(
						HttpServletResponse.SC_NOT_FOUND,
						"Benchmark does not exist or is restricted"
				);
				return;
			}
		}
	} catch (NumberFormatException nfe) {
		response.sendError(
				HttpServletResponse.SC_BAD_REQUEST,
				"The given benchmark id was in an invalid format"
		);
		return;
	} catch (Exception e) {
		response.sendError(
				HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		return;
	}
%>

<star:template title="edit ${bench.name}"
               js="lib/jquery.validate.min, edit/benchmark"
               css="edit/shared, edit/benchmark">
	<form id="editBenchmarkForm">
		<fieldset>
			<legend>benchmark details</legend>
			<table id="editBenchmark" class="shaded">
				<thead>
				<tr>
					<th>attribute</th>
					<th>value</th>
				</tr>
				</thead>
				<tbody>
				<tr>
					<td class="label">name</td>
					<td>
						<input id="name" type="text" name="name"
						       value="${bench.name}" pattern="${nameRegex}"
						       maxlength="${benchNameLen}"/>
					</td>
				</tr>
				<tr>
					<td class="label">description</td>
					<td><textarea id="description" name="description"
					              length="${benchDescLen}">${bench.description}</textarea>
					</td>
				</tr>
				<tr>
					<td class="label">type</td>
					<td>
						<select id="benchType" name="benchType">
							<c:forEach var="type" items="${types}">
								<c:choose>
									<c:when test="${type.id == bench.type.id}">
										<option selected
										        value="${type.id}">${type.name}</option>
									</c:when>
									<c:otherwise>
										<option value="${type.id}">${type.name}</option>
									</c:otherwise>
								</c:choose>
							</c:forEach>
						</select>
					</td>
				</tr>
				<tr>
					<td>downloadable</td>
					<td>
						<input id="downloadable" type="radio"
						       name="downloadable"
						       value="true" ${isDownloadable}> yes
						<input id="downloadable" type="radio"
						       name="downloadable"
						       value="false" ${isNotDownloadable}> no
					</td>
				</tr>
				</tbody>
			</table>
			<button type="button" id="delete">recycle</button>
			<button type="button" id="update">update</button>
		</fieldset>
		<div id="dialog-confirm-delete" title="confirm delete"
		     class="hiddenDialog">
			<p><span class="ui-icon ui-icon-alert"></span><span
					id="dialog-confirm-delete-txt"></span></p>
		</div>
	</form>
</star:template>
