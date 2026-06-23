const express = require('express');
const path = require('path');
const fs = require('fs').promises;
const app = express();
const port = 3000;

const WAITLIST_FILE = path.join(__dirname, 'waitlist.json');

// Use express.json() to parse JSON bodies
app.use(express.json());

// Serve static files from both landing and web directories
app.use(express.static(path.join(__dirname, '../landing')));
app.use(express.static(path.join(__dirname)));

// API Route for waitlist
app.post('/api/waitlist', async (req, res) => {
    const { email } = req.body;
    const signup = { 
        email, 
        timestamp: new Date().toISOString() 
    };
    
    console.log(`New waitlist signup: ${email}`);

    try {
        // 1. Store in local JSON file
        let waitlist = [];
        try {
            const data = await fs.readFile(WAITLIST_FILE, 'utf8');
            waitlist = JSON.parse(data);
        } catch (err) {
            // File doesn't exist yet
        }
        waitlist.push(signup);
        await fs.writeFile(WAITLIST_FILE, JSON.stringify(waitlist, null, 2));

        // 2. Notification (Scaffolded)
        // Note: To send actual emails, we need an API key or SMTP config.
        // We've scaffolded Resend integration below.
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
        
        console.log(`Signup recorded for ${email}`);
        res.status(200).json({ message: 'Signup successful' });
    } catch (error) {
        console.error('Error processing waitlist signup:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

app.listen(port, '0.0.0.0', () => {
    console.log(`SkipVox landing page listening at http://0.0.0.0:${port}`);
});
