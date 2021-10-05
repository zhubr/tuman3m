/*
 * Copyright 2011-2021 Nikolai Zhubr <zhubr@mail.ru>
 *
 * This file is provided under the terms of the GNU General Public
 * License version 2. Please see LICENSE file at the uppermost 
 * level of the repository.
 * 
 * Unless required by applicable law or agreed to in writing, this
 * software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OF ANY KIND.
 *
 */
package aq2status;

import java.io.*;

import javax.servlet.http.*;
import javax.servlet.*;

import aq2db.*;


public class ServletMain extends HttpServlet {

    public void doGet (HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        PrintWriter out = res.getWriter();
        out.println("Started... ");
        out.println(Tum3Db.getInstance(null).GetStatusInfo());
        out.println("Done. ");
        out.close();
    }
}
