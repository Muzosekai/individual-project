from flask import Flask, request, jsonify
from flask_bcrypt import Bcrypt
from flask_cors import CORS
import jwt
import datetime
import pymysql
import os
from dotenv import load_dotenv
from werkzeug.utils import secure_filename
from flask import send_from_directory
from functools import wraps
from openai import OpenAI, APIError, RateLimitError, AuthenticationError, APIConnectionError 
import json 

load_dotenv(dotenv_path='C:/PythonFlaskServers/db.env')
    
app = Flask(__name__)
CORS(app)
bcrypt = Bcrypt(app)

UPLOAD_FOLDER = os.path.join(os.getcwd(), 'uploads')
ALLOWED_EXTENSIONS = {'pdf', 'png', 'jpg', 'jpeg', 'doc', 'docx'}
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

# Flask secret key
app.config['SECRET_KEY'] = os.getenv('SECRET_KEY', 'default-secret')

client = OpenAI(api_key=os.getenv('OPENAI_API_KEY'))

    
def token_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = None
        if 'Authorization' in request.headers:
            auth_header = request.headers['Authorization']
            if auth_header.startswith('Bearer '):
                token = auth_header.split(" ")[1]

        if not token:
            return jsonify({'error': True, 'message': 'Token is missing!'}), 401

        try:
            data = jwt.decode(token, app.config['SECRET_KEY'], algorithms=["HS256"])
            current_user_id = data['user_id']
        except jwt.ExpiredSignatureError:
            return jsonify({'error': True, 'message': 'Token has expired!'}), 401
        except jwt.InvalidTokenError:
            return jsonify({'error': True, 'message': 'Token is invalid!'}), 401
        except Exception as e:
            return jsonify({'error': True, 'message': f'Token processing error: {str(e)}'}), 401

        return f(current_user_id, *args, **kwargs)
    return decorated

@app.route('/users/dashboard', methods=['GET'])
@token_required
def get_dashboard_data(current_user_id):
    conn = None
    cursor = None
        
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        query_deadlines = """
             SELECT i.id, i.title AS internship_title, i.company_name, i.deadline_date
             FROM internships i
             JOIN bookmarked_internships bi ON i.id = bi.internship_id
             WHERE bi.user_id = %s AND i.deadline_date >= CURDATE()
             ORDER BY i.deadline_date ASC
             LIMIT 5 
        """
             
        cursor.execute(query_deadlines, (current_user_id,))
        deadlines_raw = cursor.fetchall()

        from datetime import datetime # Eğer import edilmediyse
        formatted_deadlines = []
        if deadlines_raw:
            for d in deadlines_raw:
                # d['deadline_date'] zaten bir datetime.date objesi olmalı eğer DB'de DATE ise
                # veya pymysql string olarak döndürüyorsa önce parse et
                try:
                    date_obj = d['deadline_date']
                    if isinstance(d['deadline_date'], str): # Eğer DB'den string geliyorsa
                         date_obj = datetime.strptime(d['deadline_date'], '%Y-%m-%d').date()

                    # Android'in beklediği formata çevir (örn: "May 17")
                    if date_obj >= datetime.date.today(): # Filter for future/current deadlines
                        formatted_deadlines.append({
                            'id': d_row['id'],
                            'internship_title': d_row['internship_title'],
                            'company_name': d_row['company_name'],
                            'deadline_date': date_obj.strftime('%b %d') # Format as "May 17"
                        })
                except (ValueError, TypeError) as e:
                    print(f"Date formatting error for {d['deadline_date']}: {e}")
                    # Hatalı formatta olanları atla veya farklı işle
                    formatted_deadlines.append({
                        'id': d['id'],
                        'internship_title': d['internship_title'],
                        'company_name': d['company_name'],
                        'deadline_date': str(d.get('deadline_date', 'N/A')) # Orijinal string veya N/A
                    })

        deadlines = formatted_deadlines

        # 1. Kullanıcının özgeçmiş ilerlemesini çek
        #    users tablosunda resume_progress alanı olduğunu varsayıyoruz.
        #    Eğer bu alan yoksa, ALTER TABLE users ADD COLUMN resume_progress INT DEFAULT 0;
        #    ile ekleyebilirsiniz. Şimdilik sabit bir değer veya basit bir mantıkla döndürelim.
        cursor.execute("SELECT resume_progress FROM users WHERE id = %s", (current_user_id,))
        user_progress = cursor.fetchone()
        resume_progress = user_progress['resume_progress'] if user_progress else 0
        
        # Örnek: Resume progress'i portföydeki öğe sayısına göre dinamik yapalım.
        cursor.execute("SELECT COUNT(*) as portfolio_count FROM portfolio_items WHERE user_id = %s", (current_user_id,))
        portfolio_count = cursor.fetchone()['portfolio_count']
        # Basit bir mantık: her portföy öğesi %10 ilerleme sağlar (maks %50 diyelim)
        resume_progress_from_portfolio = min(portfolio_count * 10, 50)
        # Gerçek resume_progress (eğer users tablosunda tutuluyorsa) + portföyden gelen
        # resume_progress = (user_progress['resume_progress'] if user_progress else 0) + resume_progress_from_portfolio
        # Şimdilik sadece portföy sayısına göre bir ilerleme gösterelim:
        actual_resume_progress = resume_progress_from_portfolio # Veya daha karmaşık bir hesaplama

        # 2. Yaklaşan son başvuru tarihlerini çek
        #    Örnek: Kullanıcının yer imlerine eklediği ve henüz geçmemiş stajlar
        #    NOT: `internships.deadline_date` formatı "YYYY-MM-DD" olmalı veya karşılaştırılabilir olmalı
        query_deadlines = """
            SELECT i.id, i.title AS internship_title, i.company_name, i.deadline_date
            FROM internships i
            JOIN bookmarked_internships bi ON i.id = bi.internship_id
            WHERE bi.user_id = %s AND STR_TO_DATE(i.deadline_date, '%Y-%m-%d') >= CURDATE()  -- Veya deadline_date formatına uygun sorgu
            ORDER BY STR_TO_DATE(i.deadline_date, '%Y-%m-%d') ASC
            LIMIT 5 
        """ # Sadece ilk 5 yaklaşan tarih
        # Eğer deadline_date 'Apr 25' gibi bir string ise, bu sorgu çalışmaz. 
        # O zaman ya formatı YYYY-MM-DD yapın ya da Python'da filtreleyin.
        # Şimdilik 'YYYY-MM-DD' formatında olduğunu varsayalım.
        
        # Geçici olarak, `deadline_date` formatı Android tarafında `Apr 25` gibi olduğu için
        # ve backend'de `VARCHAR` olabileceği için, tüm yer imlerini çekip Python'da filtreleyebiliriz.
        # Ya da staj eklerken `deadline_date_iso` gibi bir ISO formatlı tarih de saklayabiliriz.
        # Şimdilik basit bir örnek:
        cursor.execute("""
            SELECT i.id, i.title AS internship_title, i.company_name, i.deadline_date
            FROM internships i
            JOIN bookmarked_internships bi ON i.id = bi.internship_id
            WHERE bi.user_id = %s
            ORDER BY i.id DESC 
            LIMIT 5
        """, (current_user_id,))
        deadlines_raw = cursor.fetchall()
        
        # Burada `deadline_date`'i Android'in beklediği formata getirebilir veya
        # Android tarafında parse edebilirsiniz. Android `Apr 25` bekliyorsa ve DB'de `YYYY-MM-DD` ise:
        # from datetime import datetime
        # formatted_deadlines = []
        # for d in deadlines_raw:
        #     try:
        #         date_obj = datetime.strptime(d['deadline_date'], '%Y-%m-%d')
        #         d['deadline_date'] = date_obj.strftime('%b %d') # Örn: May 17
        #     except (ValueError, TypeError):
        #         pass # Eğer format hatalıysa veya null ise orijinal kalsın
        #     formatted_deadlines.append(d)
        # deadlines = formatted_deadlines
        deadlines = deadlines_raw # Şimdilik ham veriyi yolluyoruz

        return jsonify({
            'error': False,
            'resume_progress': actual_resume_progress, # Güncellenmiş resume_progress
            'deadlines': deadlines
        }), 200

    except Exception as e:
        print(f"Dashboard data error: {e}")
        return jsonify({'error': True, 'message': f'Failed to retrieve dashboard data: {str(e)}'}), 500
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()

# Örnek staj verileri (veritabanında internship tablosu yoksa veya boşsa test için)
# sample_internships = [
#     {'id': '1', 'title': 'Software Engineer Intern', 'company_name': 'Tech Solutions Inc.', 'location': 'Remote', 'deadline': '2025-06-15', 'category': 'Tech', 'company_logo_url': None, 'is_bookmarked': False},
#     {'id': '2', 'title': 'UX Design Intern', 'company_name': 'Creative Designs LLC', 'location': 'New York, NY', 'deadline': '2025-07-01', 'category': 'Design', 'company_logo_url': None, 'is_bookmarked': False},
#     {'id': '3', 'title': 'Data Science Intern', 'company_name': 'Analytics Corp', 'location': 'San Francisco, CA', 'deadline': 'Apply by Jun 1st', 'category': 'Tech', 'company_logo_url': None, 'is_bookmarked': False},
# ]

@app.route('/ai/career-suggestions', methods=['POST'])
@token_required # Ensures only logged-in users can access
def get_ai_career_suggestions(current_user_id):
    try:
        data = request.get_json()
        if not data:
            return jsonify({'error': True, 'message': 'Request body is missing or not JSON'}), 400

        user_interests = data.get('interests')
        user_skills = data.get('skills')
        user_goals = data.get('goals', '') # Optional

        if not user_interests or not user_skills:
            return jsonify({'error': True, 'message': 'Interests and skills are required.'}), 400

        # --- 1. Construct the Prompt for OpenAI ---
       
        system_prompt = """
        You are an expert career advisor. Your task is to provide career suggestions.
Based on the user's interests, skills, and optionally their goals, you will generate:
1.  A list of 2 to 3 suggested career paths. For each career path, you must include:
    * "career_name": A string representing the name of the career.
    * "match_percentage": An integer between 0 and 100 indicating how good a match this career is.
    * "reasoning": A short string explaining why this career is a good match.
2.  A list of 2 to 3 key skill gaps the user might have for these suggested careers. For each skill gap, you must include:
    * "skill_needed": A string for the name of the skill.
    * "reasoning": A short string explaining why this skill is important or relevant.
3.  A single string for "general_advice" offering a brief piece of actionable career development advice.

Your entire response MUST be a single, valid JSON object.
Do NOT include any text before or after the JSON object.
Do NOT use markdown formatting (like ```json ... ```) around the JSON.
The JSON object must strictly follow this structure:
{
  "suggested_careers": [
    {"career_name": "Example Career 1", "match_percentage": 90, "reasoning": "Example reasoning 1."},
    {"career_name": "Example Career 2", "match_percentage": 80, "reasoning": "Example reasoning 2."}
  ],
  "skill_gaps": [
    {"skill_needed": "Example Skill 1", "reasoning": "Reason skill 1 is needed."},
    {"skill_needed": "Example Skill 2", "reasoning": "Reason skill 2 is needed."}
  ],
  "general_advice": "Example general advice."
}
        """.strip() # .strip() removes any leading/trailing blank lines from this instruction string itself.

            # Part B: The User-Specific Input for this Request
            # This is where you inject the actual data from your app user.
        user_input_for_ai = f"User Interests: {user_interests}\nUser Skills: {user_skills}"
            
            # Optionally add the user's goals if they provided them:
        if user_goals: # user_goals comes from data.get('goals', '')
            user_input_for_ai += f"\nUser Career Goals: {user_goals}"

            # Now, these two strings (`system_prompt` and `user_input_for_ai`) will be sent to OpenAI.
            # In the next step (calling OpenAI), they will be put into a "messages" list:
            # messages=[
            #     {"role": "system", "content": system_prompt},
            #     {"role": "user", "content": user_input_for_ai}
            # ]
            # ... (the rest of the function continues to call openai.ChatCompletion.create) ...
            
        user_content = f"User Interests: {user_interests}\nUser Skills: {user_skills}"
        if user_goals:
            user_content += f"\nUser Career Goals: {user_goals}"

        # --- 2. Call OpenAI API ---
        # Using the ChatCompletion endpoint (recommended)
        try:
            chat_completion_response = client.chat.completions.create(
                model="gpt-3.5-turbo", # Or gpt-4 if you have access and prefer
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_input_for_ai}
                ],
                temperature=0.7, # Adjust for creativity. Lower for more deterministic output.
                max_tokens=700  # Adjust based on expected output length
            )
            ai_generated_content_string = chat_completion_response.choices[0].message.content
            print(f"----- OpenAI Raw Response Content -----\n{ai_generated_content_string}\n------------------------------------")
            
        except RateLimitError as e:
            print(f"OpenAI API Rate Limit Error: {str(e)}")
            return jsonify({'error': True, 'message': 'AI service is temporarily busy (rate limit). Please try again later.'}), 429
        except AuthenticationError as e:
            print(f"OpenAI API Authentication Error: {str(e)}")
            return jsonify({'error': True, 'message': 'AI service authentication failed. Check API key.'}), 401
        except APIConnectionError as e:
            print(f"OpenAI API Connection Error: {str(e)}")
            return jsonify({'error': True, 'message': 'Could not connect to AI service. Please check network.'}), 503    
        except APIError as e: # Catch other OpenAI API errors
            print(f"OpenAI API Error: {str(e)}")
            return jsonify({'error': True, 'message': f'AI service error: {str(e)}'}), 503
        except Exception as e: # Catch any other unexpected error during the API call itself
            print(f"Unexpected error during OpenAI call: {str(e)}")
            return jsonify({'error': True, 'message': f'Unexpected error calling AI service: {str(e)}'}), 500

        # ... (parse ai_generated_content_string with json.loads) ...
        # ... (return jsonify response) ...



        if ai_generated_content_string is None:
            # This case should ideally be caught by the exceptions above if the API call failed.
            # But as a safeguard:
            print("Error: ai_generated_content_string is None after OpenAI call block, though no specific OpenAI error was caught.")
            return jsonify({'error': True, 'message': 'Failed to retrieve content from AI service.'}), 500

            # 4. Parse AI's response
        try:
            suggestions_data = json.loads(ai_generated_content_string)
            return jsonify({'error': False, 'suggestions': suggestions_data}), 200
        except json.JSONDecodeError as e:
            print(f"JSONDecodeError: Failed to parse AI's response as JSON. Error: {str(e)}")
            return jsonify({
                'error': True,
                'message': "AI returned data in an unexpected format. Could not parse suggestions.",
                'raw_ai_response': ai_generated_content_string
            }), 500

    except Exception as e: # Outer try-except for the whole function
       print(f"FATAL error in /ai/career-suggestions endpoint: {str(e)}") # Changed print for clarity
       # This outer catch is critical.
       return jsonify({'error': True, 'message': f'An unexpected internal server error occurred: {str(e)}'}), 500

    # It's highly unlikely to reach here with the structure above,
    # but as a final failsafe (though bad practice to rely on it):
    # print("Error: Function reached end without explicit return after outer try-except.")
    # return jsonify({'error': True, 'message': 'Server error: Reached end of function unexpectedly.'}), 500


@app.route('/internships', methods=['GET'])
# @token_required # Stajları görmek için token gerekmeyebilir, ama yer imi bilgisi için gerekebilir.
# Eğer yer imi bilgisini her staj için göndereceksek token_required kullanmalıyız.
# Şimdilik token olmadan stajları listeleyelim. Yer imi için ayrı endpoint daha iyi olabilir.
def get_internships(): # Eğer token_required eklenirse current_user_id parametresi alır
    conn = None
    cursor = None
    try:
        search_query = request.args.get('query') # örn: "Software Engineer"
        filters_str = request.args.get('filters') # örn: "Tech,Remote" Android'den gelen
        
        # Temel SQL sorgusu
        sql_query = "SELECT * FROM internships" # internships tablonuzun adını ve sütunlarını kontrol edin
        conditions = []
        params = []

        if search_query:
            conditions.append("(title LIKE %s OR company_name LIKE %s OR description LIKE %s)")
            search_param = f"%{search_query}%"
            params.extend([search_param, search_param, search_param])

        if filters_str:
            # Android'den gelen filtreler "Tech,Remote" şeklinde ise
            # filters_list = [f.strip() for f in filters_str.split(',') if f.strip()]
            # Eğer Android'den List<String> olarak gelip Retrofit Query'de join ediliyorsa
            # ve backend'e tek bir string olarak ulaşıyorsa
            
            # Android tarafında List<String> olarak gönderip Retrofit'in @Query("filters") List<String>
            # şeklinde almasını ve Flask'ın request.args.getlist("filters") ile almasını sağlayabiliriz.
            # Şu anki Android kodu tek bir string yolluyor gibi görünüyor.
            
            filters_list = [f.strip().lower() for f in filters_str.split(',') if f.strip()] # Küçük harfe çevir
            if filters_list:
                # Her filtre için ayrı bir LIKE koşulu veya kategori için IN operatörü
                # Basit bir örnek olarak, category veya location için filtreleme
                filter_conditions = []
                for flt_item in filters_list:
                    # Burada category veya location gibi belirli alanlarda arama yapılabilir.
                    # Veya genel bir arama (daha az verimli olabilir)
                    filter_conditions.append("(LOWER(category) = %s OR LOWER(location) LIKE %s)")
                    params.extend([flt_item, f"%{flt_item}%"])
                
                if filter_conditions:
                    conditions.append("(" + " OR ".join(filter_conditions) + ")")


        if conditions:
            sql_query += " WHERE " + " AND ".join(conditions)
        
        sql_query += " ORDER BY post_date DESC" # En yeni stajlar önce

        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute(sql_query, tuple(params))
        internships_data = cursor.fetchall()
        
        # Eğer token_required kullanılırsa ve kullanıcı ID'si varsa, her staj için yer imi durumunu ekleyebiliriz.
        # current_user_id = getattr(g, 'user_id', None) # Eğer g objesinde saklanıyorsa
        # if current_user_id and internships_data:
        # for internship in internships_data:
        # cursor.execute("SELECT EXISTS(SELECT 1 FROM bookmarked_internships WHERE user_id = %s AND internship_id = %s)", (current_user_id, internship['id']))
        # internship['is_bookmarked'] = bool(cursor.fetchone()[0])

        return jsonify({'error': False, 'internships': internships_data}), 200

    except Exception as e:
        print(f"Get internships error: {e}")
        # return jsonify({'error': False, 'internships': sample_internships}) # Test için
        return jsonify({'error': True, 'message': f'Failed to retrieve internships: {str(e)}'}), 500
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()


# API Endpoint 2: `POST /internships/<internship_id>/apply`
# Veya Android `applyForInternship(@Path("internshipId") internshipId: String)` buna uygun.
@app.route('/internships/<int:internship_id>/apply', methods=['POST'])
@token_required
def apply_for_internship(current_user_id, internship_id):
    conn = None
    cursor = None
    try:
        conn = get_db_connection()
        cursor = conn.cursor()

        # Stajın var olup olmadığını kontrol et
        cursor.execute("SELECT id FROM internships WHERE id = %s", (internship_id,))
        if not cursor.fetchone():
            return jsonify({'error': True, 'message': 'Internship not found.'}), 404

        # Kullanıcının daha önce başvurup başvurmadığını kontrol et (opsiyonel)
        cursor.execute("SELECT id FROM internship_applications WHERE user_id = %s AND internship_id = %s", 
                       (current_user_id, internship_id))
        if cursor.fetchone():
            return jsonify({'error': True, 'message': 'You have already applied for this internship.'}), 409

        # Başvuruyu kaydet
        cursor.execute("INSERT INTO internship_applications (user_id, internship_id) VALUES (%s, %s)",
                       (current_user_id, internship_id))
        conn.commit()

        return jsonify({'error': False, 'message': 'Application submitted successfully.'}), 201

    except Exception as e:
        print(f"Apply internship error: {e}")
        return jsonify({'error': True, 'message': f'Failed to submit application: {str(e)}'}), 500
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()

# API Endpoint 3 & 4: Bookmark (Yer İmi) İşlemleri
@app.route('/users/bookmarks/<int:internship_id>', methods=['POST']) # Add bookmark
@token_required
def add_bookmark(current_user_id, internship_id):
    conn = None
    cursor = None
    try:
        conn = get_db_connection()
        cursor = conn.cursor()

        # Stajın var olup olmadığını kontrol et
        cursor.execute("SELECT id FROM internships WHERE id = %s", (internship_id,))
        if not cursor.fetchone():
            return jsonify({'error': True, 'message': 'Internship not found.'}), 404
        
        # Zaten eklenmiş mi kontrol et
        cursor.execute("SELECT * FROM bookmarked_internships WHERE user_id = %s AND internship_id = %s", (current_user_id, internship_id))
        if cursor.fetchone():
            return jsonify({'error': False, 'message': 'Internship already bookmarked.'}), 200 # Veya 409

        cursor.execute("INSERT INTO bookmarked_internships (user_id, internship_id) VALUES (%s, %s)",
                       (current_user_id, internship_id))
        conn.commit()
        return jsonify({'error': False, 'message': 'Internship bookmarked successfully.'}), 201

    except pymysql.err.IntegrityError: # Eğer PK ihlali olursa (zaten ekliyse)
        return jsonify({'error': False, 'message': 'Internship already bookmarked (handled).'}), 200
    except Exception as e:
        print(f"Add bookmark error: {e}")
        return jsonify({'error': True, 'message': f'Failed to bookmark internship: {str(e)}'}), 500
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()


@app.route('/users/bookmarks/<int:internship_id>', methods=['DELETE']) # Remove bookmark
@token_required
def remove_bookmark(current_user_id, internship_id):
    conn = None
    cursor = None
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        result = cursor.execute("DELETE FROM bookmarked_internships WHERE user_id = %s AND internship_id = %s",
                                (current_user_id, internship_id))
        conn.commit()

        if result > 0:
            return jsonify({'error': False, 'message': 'Bookmark removed successfully.'}), 200
        else:
            return jsonify({'error': False, 'message': 'Bookmark not found or already removed.'}), 200 # Veya 404

    except Exception as e:
        print(f"Remove bookmark error: {e}")
        return jsonify({'error': True, 'message': f'Failed to remove bookmark: {str(e)}'}), 500
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()

# individualProject.py

# ... (UPLOAD_FOLDER, ALLOWED_EXTENSIONS, allowed_file fonksiyonu zaten var) ...

@app.route('/portfolio', methods=['POST'])
@token_required
def upload_portfolio(current_user_id):
    # 1. Check if the file part is in the request
    if 'portfolio_file' not in request.files:
        return jsonify({'error': True, 'message': 'No file part in the request (portfolio_file is missing)'}), 400

    file = request.files['portfolio_file']

    # 2. Check if a file was selected
    if file.filename == '':
        return jsonify({'error': True, 'message': 'No file selected'}), 400

    # 3. Check if the file object is valid and (optionally) if the file type is allowed
    if file and allowed_file(file.filename): # Make sure allowed_file function is defined and works
        conn = None  # Initialize conn and cursor outside try for finally block
        cursor = None
        try:
            original_filename = file.filename # Keep original filename if needed for display
            filename = secure_filename(file.filename) # Sanitize filename for saving

            # User-specific upload folder
            user_upload_folder = os.path.join(app.config['UPLOAD_FOLDER'], str(current_user_id))
            if not os.path.exists(user_upload_folder):
                os.makedirs(user_upload_folder, exist_ok=True)
            
            save_path = os.path.join(user_upload_folder, filename)
            file.save(save_path)
            
            file_type = filename.rsplit('.', 1)[1].lower() if '.' in filename else None
            
            # Path to be stored in DB and used for constructing URL
            # Store as user_id/filename for easier URL construction later
            db_file_path_for_url = f"{current_user_id}/{filename}"

            conn = get_db_connection()
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO portfolio_items (user_id, file_name, file_path, file_type) 
                VALUES (%s, %s, %s, %s)
            """, (current_user_id, original_filename, db_file_path_for_url, file_type)) # Save original_filename for display, use db_file_path_for_url for path
            conn.commit()
            portfolio_item_id = cursor.lastrowid

            # Construct the full URL for the client to access the file
            # Assumes you have a route like /uploads/<user_id>/<filename> (which you do: uploaded_file_user_specific)
            file_url = f"{request.host_url.rstrip('/')}/uploads/{db_file_path_for_url}"
            # request.host_url might include http:// or https:// and port

            return jsonify({
                'error': False,
                'message': 'File uploaded successfully',
                'filename': original_filename, # Return original filename
                'path': file_url,             # This is the accessible URL
                'file_url': file_url,         # Consistent with PortfolioItem model
                'portfolio_item_id': portfolio_item_id
            }), 200

        except Exception as e:
            print(f"Upload error: {e}") # Log the full error for debugging
            if conn:
                conn.rollback() # Rollback DB changes if an error occurs after connection
            return jsonify({'error': True, 'message': f'Upload failed: {str(e)}'}), 500
        finally:
            if cursor:
                cursor.close()
            if conn:
                conn.close()
    elif not allowed_file(file.filename):
        return jsonify({'error': True, 'message': f'File type not allowed. Allowed types: {ALLOWED_EXTENSIONS}'}), 400
    else:
        # This case might not be reachable if the above checks are exhaustive
        return jsonify({'error': True, 'message': 'Invalid file or unknown upload error'}), 400

# API Endpoint 2: `GET /portfolio`
#Giriş yapmış kullanıcının tüm portföy öğelerini listeler.

@app.route('/portfolio', methods=['GET'])
@token_required
def get_portfolio_items(current_user_id):
    conn = None
    cursor = None
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # user_id, file_name, file_path (bu, DB'deki relative path olabilir), file_type, upload_date
        # Android'in `fileUrl` için tam bir URL'ye ihtiyacı var.
        # `thumbnail_url` şimdilik null olabilir veya dosya türüne göre bir placeholder olabilir.
        cursor.execute("""
            SELECT id, user_id, file_name, file_path, file_type, upload_date,
                   CONCAT(%s, 'uploads/', file_path) AS file_url,
                   NULL AS thumbnail_url 
            FROM portfolio_items 
            WHERE user_id = %s 
            ORDER BY upload_date DESC
        """, (request.host_url, current_user_id)) # request.host_url = "[http://192.168.](http://192.168.)x.x:5000/"
        
        items = cursor.fetchall()

        return jsonify({'error': False, 'portfolio_items': items}), 200

    except Exception as e:
        print(f"Get portfolio items error: {e}")
        return jsonify({'error': True, 'message': f'Failed to retrieve portfolio items: {str(e)}'}), 500
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()



# 📦 Native pymysql connector
def get_db_connection():
    return pymysql.connect(
        host=os.getenv('MYSQL_HOST', 'localhost'),
        user=os.getenv('MYSQL_USER', 'root'),
        password=os.getenv('MYSQL_PASSWORD', ''),
        db=os.getenv('MYSQL_DB', 'smart_portfolio_db'),
        cursorclass=pymysql.cursors.DictCursor
    )


# Helper function to check file types
def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


@app.route('/uploads/<int:user_id>/<filename>') # Veya daha genel bir path alıcı
def uploaded_file_user_specific(user_id, filename):
    user_folder = os.path.join(app.config['UPLOAD_FOLDER'], str(user_id))
    return send_from_directory(user_folder, filename)

# ✅ Register
@app.route('/users/register', methods=['POST'])
def register_user():
    conn = None
    cursor = None
    try:
        data = request.get_json()
        name = data.get('name')
        email = data.get('email')
        password = data.get('password')

        if not name or not email or not password:
            return jsonify({'error': True, 'message': 'Please fill all fields.'}), 400
        if len(password) < 6:
            return jsonify({'error': True, 'message': 'Password must be at least 6 characters.'}), 400

        conn = get_db_connection()
        cursor = conn.cursor()

        cursor.execute("SELECT id FROM users WHERE email = %s", (email,))
        if cursor.fetchone():
            return jsonify({'error': True, 'message': 'Email already registered.'}), 409

        hashed_password = bcrypt.generate_password_hash(password).decode('utf-8')
        cursor.execute("INSERT INTO users (name, email, password) VALUES (%s, %s, %s)",
                       (name, email, hashed_password))
        conn.commit()
        user_id = cursor.lastrowid

        return jsonify({'error': False, 'message': 'User registered successfully.', 'user_id': user_id}), 201

    except Exception as e:
        print(f"Registration error: {e}")
        return jsonify({'error': True, 'message': 'Registration failed. Please try again.'}), 500

    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()

# ✅ Login
@app.route('/users/login', methods=['POST'])
def login_user():
    conn = None
    cursor = None
    try:
        data = request.get_json()
        email = data.get('email')
        password = data.get('password')

        if not email or not password:
            return jsonify({'error': True, 'message': 'Email and password are required.'}), 400

        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT id, name, email, password FROM users WHERE email = %s", (email,))
        user = cursor.fetchone()

        if user and bcrypt.check_password_hash(user['password'], password):
            token_payload = {
                'user_id': user['id'],
                'email': user['email'],
                'exp': datetime.datetime.utcnow() + datetime.timedelta(hours=24)
            }
            token = jwt.encode(token_payload, app.config['SECRET_KEY'], algorithm='HS256')

            return jsonify({
                'error': False,
                'message': 'Login successful.',
                'user': {
                    'id': user['id'],
                    'name': user['name'],
                    'email': user['email']
                },
                'token': token
            }), 200
        else:
            return jsonify({'error': True, 'message': 'Invalid email or password.'}), 401

    except Exception as e:
        print(f"Login error: {e}")
        return jsonify({'error': True, 'message': 'Login failed. Please try again.'}), 500

    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()

# ✅ Forgot Password (Simulated)
@app.route('/users/forgot_password', methods=['POST'])
def forgot_password():
    conn = None
    cursor = None
    try:
        data = request.get_json()
        email = data.get('email')

        if not email:
            return jsonify({'error': True, 'message': 'Email address is required.'}), 400

        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT id FROM users WHERE email = %s", (email,))
        user_exists = cursor.fetchone()

        if user_exists:
            return jsonify({'error': False, 'message': 'If your email is registered, you will receive a password reset link shortly. (Simulated)'}), 200
        else:
            return jsonify({'error': True, 'message': 'Email not found.'}), 404

    except Exception as e:
        print(f"Forgot password error: {e}")
        return jsonify({'error': True, 'message': 'Failed to process request.'}), 500

    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()

# ✅ Run server
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
