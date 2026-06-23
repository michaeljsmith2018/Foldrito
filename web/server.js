const express = require('express');
const path = require('path');
const app = express();
const port = 3000;

// Use express.json() to parse JSON bodies
app.use(express.json());

// Serve static files from both landing and web directories
// Prioritize landing/ for the frontend assets and index.html
app.use(express.static(path.join(__dirname, '../landing')));
app.use(express.static(path.join(__dirname)));

// API Route for waitlist
app.post('/api/waitlist', async (req, res) => {
    const { email } = req.body;
    console.log(`New waitlist signup: ${email}`);

    try {
        // We will integrate Resend here once credentials arrive
        // For now, we'll log it and return success
        
        /* 
        const { Resend } = require('resend');
        const resend = new Resend(process.env.RESEND_API_KEY);
        
        await resend.emails.send({
            from: 'SkipVox Waitlist <onboarding@resend.dev>',
            to: 'skipvox-2eb46101@ctomail.io',
            subject: 'New Waitlist Signup',
            html: `<p>New user signed up for the waitlist: <strong>${email}</strong></p>`
        });
        */

        res.status(200).json({ message: 'Signup successful' });
    } catch (error) {
        console.error('Error processing waitlist signup:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

app.listen(port, '0.0.0.0', () => {
    console.log(`SkipVox landing page listening at http://0.0.0.0:${port}`);
});
